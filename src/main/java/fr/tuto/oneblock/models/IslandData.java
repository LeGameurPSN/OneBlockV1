package fr.tuto.oneblock.models;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IslandData {

    private UUID owner;
    private final Location blockLocation;
    private int brokenCount;
    /**
     * Total de blocs cassés à VIE, TOUTES renaissances confondues. Contrairement
     * à brokenCount (qui sert à calculer le niveau OneBlock actuel et est donc
     * remis à 0 à chaque renaissance, voir OBCommand#rebirthIsland), ce compteur
     * n'est jamais réinitialisé : il sert uniquement au classement /top "Blocs
     * cassés", pour refléter l'activité réelle d'un joueur/île même après
     * plusieurs renaissances, au lieu de favoriser artificiellement les îles
     * qui n'ont jamais fait de renaissance.
     */
    private int totalBrokenCount;
    private Material currentMaterial;
    private EntityType currentSpawnerEntity; // null si le bloc actuel n'est pas un spawner
    private int borderSize;
    private final Map<String, Boolean> settings = new HashMap<>();
    /**
     * Réglages d'île propres au Nether (/ob settings dans le Nether), INDÉPENDANTS
     * de "settings" ci-dessus (overworld) : une île peut donc, par exemple, avoir le
     * PVP activé dans son Nether mais désactivé sur son île overworld, ou inversement.
     * Voir getSetting(World, ...) / setSetting(World, ...) ci-dessous.
     */
    private final Map<String, Boolean> netherSettings = new HashMap<>();
    private transient boolean regenerating;
    // Horodatage (millis) de la dernière casse VALIDÉE du bloc OneBlock de
    // cette île. Sert au cooldown anti-spam de BlockBreakListener : deux
    // casses trop rapprochées (< 650ms) peuvent arriver la même tick côté
    // serveur (double-clic très rapide, autoclicker, désync client/serveur)
    // et provoquer une régénération corrompue du bloc (bloc qui ne repop
    // plus). Non sauvegardé sur disque : redémarre à 0 à chaque chargement,
    // ce qui est sans conséquence (le pire cas est une seule casse autorisée
    // en plus juste après un redémarrage).
    private transient long lastBreakMillis = 0L;

    // Horodatage (millis) du dernier changement du réglage "pvp" via le GUI
    // des paramètres. Sert au cooldown anti-spam de GuiListener : le PVP ne
    // peut être basculé (ON<->OFF) qu'une fois toutes les 10 minutes par
    // île, pour éviter les allers-retours abusifs (ex: activer juste pour
    // frapper quelqu'un puis redésactiver aussitôt). Non sauvegardé sur
    // disque : redémarre à 0 à chaque chargement, ce qui autorise un
    // changement immédiat juste après un redémarrage du serveur.
    private transient long lastPvpToggleMillis = 0L;

    private double bankBalance;   // caisse d'île ("banque IS"), séparée du compte perso (Vault)
    /**
     * Contenu du coffre partagé de la caisse d'île (/bankis objets) : un
     * simple tableau de 54 cases (double coffre), à plat comme
     * Inventory#getContents(). Rempli à null par défaut (coffre vide).
     * La "vraie" copie vivante pendant que des joueurs ont le GUI ouvert
     * vit dans l'Inventory mis en cache par OneBlockManager
     * (getOrCreateBankInventory) ; ce tableau n'est qu'un instantané
     * resynchronisé avant chaque sauvegarde (voir OneBlockManager#saveIslands).
     */
    private ItemStack[] bankItems = new ItemStack[54];
    private int rebirths;         // nombre de renaissances effectuées
    private int mobsKilled;       // total de mobs tués par le propriétaire de l'île

    // Tokens par joueur (uuid -> montant) : chaque membre de l'équipe a son
    // propre solde de tokens, contrairement à la banque/argent d'île qui
    // reste partagée. LinkedHashMap pour un ordre d'affichage stable
    // (propriétaire ajouté en premier via getTokens/addTokens à la demande).
    private final Map<UUID, Integer> playerTokens = new LinkedHashMap<>();
    private boolean flyUnlocked;  // true si le vol ("fly") a été acheté dans /token
    private boolean hatUnlocked;  // true si l'accès à /hat a été acheté dans /token
    private boolean feedUnlocked; // true si l'accès à /feed a été acheté dans /token

    // Niveaux des améliorations achetées avec de l'argent (/upgrades), clé -> niveau
    private final Map<String, Integer> upgradeLevels = new HashMap<>();
    // Niveaux des améliorations achetées avec des points de renaissance (/rebirthshop), clé -> niveau
    private final Map<String, Integer> rebirthShopLevels = new HashMap<>();
    private int rebirthPoints; // monnaie de prestige gagnée à chaque renaissance, dépensable dans /rebirthshop

    // ==================== ÉQUIPE (/ob team, /ob kick, /ob ban, /ob permissions, /ob leave) ====================

    /** Coéquipiers de l'île (n'inclut PAS le propriétaire). Ordre d'ajout conservé pour un affichage stable. */
    private final Set<UUID> members = new LinkedHashSet<>();
    /**
     * Joueurs "de confiance" (/ob trust) : contrairement à un coéquipier
     * (members ci-dessus), un joueur de confiance GARDE sa propre île (elle
     * n'est ni supprimée, ni fusionnée) et ne fait pas partie de l'équipe
     * (pas dans /ob team list, pas de partage de banque/progression). Il
     * obtient seulement les permissions du rôle TRUST (/ob perms) sur CETTE île.
     */
    private final Set<UUID> trustedPlayers = new LinkedHashSet<>();
    /**
     * Permissions par RÔLE (/ob perms) : une table de 36 permissions précises
     * (voir IslandPermission) par rôle (VISITEUR / TRUST / MEMBRE), commune à
     * tous les joueurs de ce rôle sur cette île (contrairement à l'ancien
     * système par joueur). Initialisée à la demande avec les valeurs par
     * défaut de chaque permission ; seules les entrées modifiées par le
     * propriétaire via le GUI diffèrent ensuite des valeurs par défaut.
     */
    private final Map<Role, Map<String, Boolean>> rolePermissions = new LinkedHashMap<>();
    /** Joueurs bannis de cette île : ne peuvent plus être invités, ni visiter l'île. */
    private final Set<UUID> bannedPlayers = new HashSet<>();
    /**
     * Île verrouillée (/ob lock ou /is lock) : quand true, plus AUCUN
     * visiteur (non propriétaire, non coéquipier) ne peut la visiter via
     * /ob visit ou /is visit, quel que soit le réglage "build"/"pvp"/etc.
     * Les ops restent toujours capables de visiter, comme partout ailleurs.
     */
    private boolean locked = false;

    // ==================== DIMENSION NETHER (portail) ====================
    // Chaque île a, en plus de son OneBlock classique dans le monde
    // "is_de_<pseudo>", un second OneBlock indépendant dans un monde Nether
    // dédié "is_nether_de_<pseudo>", accessible via un portail du Nether
    // (voir NetherBlockListener). Toute l'équipe (coéquipiers/trust) y a
    // accès comme sur l'île normale : c'est la MÊME IslandData qui gère les
    // deux mondes.
    private Location netherBlockLocation;
    private int netherBrokenCount;
    private Material netherCurrentMaterial;
    private final Set<Material> netherMinedTypes = new HashSet<>();
    private transient boolean netherRegenerating;

    // ==================== DIMENSION END ====================
    // Même principe que le Nether ci-dessus, mais pour le monde
    // "is_end_de_<pseudo>" (accessible via un portail de l'End).
    private Location endBlockLocation;
    private int endBrokenCount;
    private Material endCurrentMaterial;
    private final Set<Material> endMinedTypes = new HashSet<>();
    private transient boolean endRegenerating;

    public IslandData(UUID owner, Location blockLocation, Material currentMaterial) {
        this.owner = owner;
        this.blockLocation = blockLocation;
        this.currentMaterial = currentMaterial;
        this.brokenCount = 0;
        this.totalBrokenCount = 0;
        this.borderSize = 50;
        this.regenerating = false;
        this.bankBalance = 0;
        this.rebirths = 0;
        this.mobsKilled = 0;
        this.flyUnlocked = false;
        this.hatUnlocked = false;
        this.feedUnlocked = false;
    }

    public UUID getOwner() {
        return owner;
    }

    /**
     * Change le propriétaire de cette île. Utilisé par /istransfer (transfert
     * d'île admin, ex: ancien pseudo -> nouveau pseudo). L'appelant est
     * responsable de mettre à jour la map UUID -> IslandData dans
     * OneBlockManager (retirer l'ancienne clé, ajouter la nouvelle).
     */
    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Location getBlockLocation() {
        return blockLocation;
    }

    public int getBrokenCount() {
        return brokenCount;
    }

    public void incrementBroken() {
        incrementBroken(1);
    }

    /**
     * Boost staff "x2 XP" (/boost give x2xp <joueur>, voir BoostManager) :
     * pendant les 10 minutes du boost, chaque bloc OneBlock cassé compte
     * pour 2 dans la progression de l'île (niveau), au lieu d'un seul.
     */
    public void incrementBroken(int amount) {
        this.brokenCount += amount;
        this.totalBrokenCount += amount;
    }

    public void setBrokenCount(int brokenCount) {
        this.brokenCount = brokenCount;
    }

    public int getTotalBrokenCount() {
        return totalBrokenCount;
    }

    /** Utilisé uniquement au chargement depuis le disque (voir OneBlockManager#loadIslands). */
    public void setTotalBrokenCount(int totalBrokenCount) {
        this.totalBrokenCount = totalBrokenCount;
    }

    public Material getCurrentMaterial() {
        return currentMaterial;
    }

    public void setCurrentMaterial(Material currentMaterial) {
        this.currentMaterial = currentMaterial;
    }

    public EntityType getCurrentSpawnerEntity() {
        return currentSpawnerEntity;
    }

    public void setCurrentSpawnerEntity(EntityType currentSpawnerEntity) {
        this.currentSpawnerEntity = currentSpawnerEntity;
    }

    public boolean isRegenerating() {
        return regenerating;
    }

    public void setRegenerating(boolean regenerating) {
        this.regenerating = regenerating;
    }

    public long getLastBreakMillis() {
        return lastBreakMillis;
    }

    public void setLastBreakMillis(long lastBreakMillis) {
        this.lastBreakMillis = lastBreakMillis;
    }

    public long getLastPvpToggleMillis() {
        return lastPvpToggleMillis;
    }

    public void setLastPvpToggleMillis(long lastPvpToggleMillis) {
        this.lastPvpToggleMillis = lastPvpToggleMillis;
    }

    public int getBorderSize() {
        return borderSize;
    }

    public void setBorderSize(int borderSize) {
        this.borderSize = borderSize;
    }

    public Map<String, Boolean> getSettings() {
        return settings;
    }

    public boolean getSetting(String key, boolean def) {
        return settings.getOrDefault(key, def);
    }

    public void setSetting(String key, boolean value) {
        settings.put(key, value);
    }

    /** Table des réglages du Nether de cette île, indépendante de "settings" (overworld). */
    public Map<String, Boolean> getNetherSettings() {
        return netherSettings;
    }

    /** true si {@code world} est le monde Nether dédié de cette île. */
    public boolean isNetherWorld(org.bukkit.World world) {
        return netherBlockLocation != null && world != null
                && world.equals(netherBlockLocation.getWorld());
    }

    /**
     * Réglage d'île pour un monde donné : si {@code world} est le monde Nether
     * dédié de cette île, lit dans la table Nether indépendante (netherSettings) ;
     * sinon lit dans la table overworld classique (settings). Utilisé pour que les
     * réglages d'île (pvp, mob-spawning, bordure, etc.) soient totalement
     * indépendants entre l'overworld et le Nether de chaque île.
     */
    public boolean getSetting(org.bukkit.World world, String key, boolean def) {
        if (isNetherWorld(world)) return netherSettings.getOrDefault(key, def);
        return settings.getOrDefault(key, def);
    }

    /** Équivalent en écriture de getSetting(World, String, boolean). */
    public void setSetting(org.bukkit.World world, String key, boolean value) {
        if (isNetherWorld(world)) netherSettings.put(key, value);
        else settings.put(key, value);
    }

    /**
     * Retourne true si le point (x, z) est à l'intérieur de la bordure de cette île.
     */
    public boolean contains(Location loc) {
        if (!loc.getWorld().equals(blockLocation.getWorld())) return false;
        double half = borderSize / 2.0;
        double dx = Math.abs(loc.getX() - blockLocation.getX());
        double dz = Math.abs(loc.getZ() - blockLocation.getZ());
        return dx <= half && dz <= half;
    }

    // ==================== NETHER (portail) ====================

    public Location getNetherBlockLocation() {
        return netherBlockLocation;
    }

    public void setNetherBlockLocation(Location netherBlockLocation) {
        this.netherBlockLocation = netherBlockLocation;
    }

    public boolean hasNetherIsland() {
        return netherBlockLocation != null;
    }

    public int getNetherBrokenCount() {
        return netherBrokenCount;
    }

    public void setNetherBrokenCount(int netherBrokenCount) {
        this.netherBrokenCount = netherBrokenCount;
    }

    public void incrementNetherBroken() {
        incrementNetherBroken(1);
    }

    /** Boost staff "x2 XP" (voir {@link #incrementBroken(int)}) : idem pour le OneBlock Nether. */
    public void incrementNetherBroken(int amount) {
        this.netherBrokenCount += amount;
        // Fusion demandée : un bloc miné dans le Nether compte aussi dans le
        // niveau global de l'île (compteur "normal", partagé Nether+Overworld)
        // et dans le total à vie utilisé par /top "Blocs cassés".
        this.brokenCount += amount;
        this.totalBrokenCount += amount;
    }

    public Material getNetherCurrentMaterial() {
        return netherCurrentMaterial;
    }

    public void setNetherCurrentMaterial(Material netherCurrentMaterial) {
        this.netherCurrentMaterial = netherCurrentMaterial;
    }

    /** Types de blocs pleins déjà obtenus au moins une fois dans le Nether de cette île (voir /rebirth). */
    public Set<Material> getNetherMinedTypes() {
        return netherMinedTypes;
    }

    public void recordNetherMined(Material material) {
        if (material != null) netherMinedTypes.add(material);
    }

    public boolean isNetherRegenerating() {
        return netherRegenerating;
    }

    public void setNetherRegenerating(boolean netherRegenerating) {
        this.netherRegenerating = netherRegenerating;
    }

    /** Retourne true si (x, z) est dans la bordure de cette île, DANS SON MONDE NETHER. */
    public boolean containsNether(Location loc) {
        if (netherBlockLocation == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().equals(netherBlockLocation.getWorld())) return false;
        double half = borderSize / 2.0;
        double dx = Math.abs(loc.getX() - netherBlockLocation.getX());
        double dz = Math.abs(loc.getZ() - netherBlockLocation.getZ());
        return dx <= half && dz <= half;
    }

    // ==================== END ====================

    public Location getEndBlockLocation() {
        return endBlockLocation;
    }

    public void setEndBlockLocation(Location endBlockLocation) {
        this.endBlockLocation = endBlockLocation;
    }

    public boolean hasEndIsland() {
        return endBlockLocation != null;
    }

    public int getEndBrokenCount() {
        return endBrokenCount;
    }

    public void setEndBrokenCount(int endBrokenCount) {
        this.endBrokenCount = endBrokenCount;
    }

    public void incrementEndBroken() {
        incrementEndBroken(1);
    }

    /** Boost staff "x2 XP" (voir {@link #incrementBroken(int)}) : idem pour le OneBlock End. */
    public void incrementEndBroken(int amount) {
        this.endBrokenCount += amount;
    }

    public Material getEndCurrentMaterial() {
        return endCurrentMaterial;
    }

    public void setEndCurrentMaterial(Material endCurrentMaterial) {
        this.endCurrentMaterial = endCurrentMaterial;
    }

    /** Types de blocs pleins déjà obtenus au moins une fois dans l'End de cette île (voir /rebirth). */
    public Set<Material> getEndMinedTypes() {
        return endMinedTypes;
    }

    public void recordEndMined(Material material) {
        if (material != null) endMinedTypes.add(material);
    }

    public boolean isEndRegenerating() {
        return endRegenerating;
    }

    public void setEndRegenerating(boolean endRegenerating) {
        this.endRegenerating = endRegenerating;
    }

    /** Retourne true si (x, z) est dans la bordure de cette île, DANS SON MONDE END. */
    public boolean containsEnd(Location loc) {
        if (endBlockLocation == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().equals(endBlockLocation.getWorld())) return false;
        double half = borderSize / 2.0;
        double dx = Math.abs(loc.getX() - endBlockLocation.getX());
        double dz = Math.abs(loc.getZ() - endBlockLocation.getZ());
        return dx <= half && dz <= half;
    }

    // ==================== BANQUE D'ÎLE / RENAISSANCE / STATS ====================

    public double getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(double bankBalance) {
        this.bankBalance = Math.max(0, bankBalance);
    }

    public void addBankBalance(double amount) {
        setBankBalance(this.bankBalance + amount);
    }

    /** Instantané du coffre d'objets partagé de la caisse d'île (54 cases, peut contenir des null). */
    public ItemStack[] getBankItems() {
        return bankItems;
    }

    public void setBankItems(ItemStack[] bankItems) {
        this.bankItems = bankItems;
    }

    public int getRebirths() {
        return rebirths;
    }

    public void setRebirths(int rebirths) {
        this.rebirths = rebirths;
    }

    public void incrementRebirths() {
        this.rebirths++;
    }

    public int getMobsKilled() {
        return mobsKilled;
    }

    public void setMobsKilled(int mobsKilled) {
        this.mobsKilled = mobsKilled;
    }

    public void incrementMobsKilled() {
        this.mobsKilled++;
    }

    // ==================== TOKENS / ACHATS (/token) ====================
    // Chaque JOUEUR (propriétaire ou coéquipier) a son propre solde de
    // tokens, séparé du reste de l'équipe. Utilise getTeamTokensTotal() /
    // getTeamTokensBreakdown() pour un affichage combiné "toute l'équipe".

    /** Solde de tokens propre à ce joueur (0 si jamais crédité). */
    public int getTokens(UUID uuid) {
        return playerTokens.getOrDefault(uuid, 0);
    }

    public void setTokens(UUID uuid, int tokens) {
        playerTokens.put(uuid, Math.max(0, tokens));
    }

    public void addTokens(UUID uuid, int amount) {
        setTokens(uuid, getTokens(uuid) + amount);
    }

    /** Map brute uuid -> tokens, utilisée pour la sauvegarde/chargement. */
    public Map<UUID, Integer> getPlayerTokens() {
        return playerTokens;
    }

    /**
     * Somme des tokens de TOUS les membres de l'équipe (propriétaire +
     * coéquipiers), pour un affichage/classement combiné "1 tête" par île.
     */
    public int getTeamTokensTotal() {
        int total = getTokens(owner);
        for (UUID member : members) {
            total += getTokens(member);
        }
        return total;
    }

    /**
     * Détail des tokens de chaque membre de l'équipe (propriétaire d'abord,
     * puis coéquipiers dans l'ordre d'ajout), pour affichage nominatif
     * (ex: dans la boutique à tokens ou /token).
     */
    public Map<UUID, Integer> getTeamTokensBreakdown() {
        Map<UUID, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put(owner, getTokens(owner));
        for (UUID member : members) {
            breakdown.put(member, getTokens(member));
        }
        return breakdown;
    }

    public boolean isFlyUnlocked() {
        return flyUnlocked;
    }

    public void setFlyUnlocked(boolean flyUnlocked) {
        this.flyUnlocked = flyUnlocked;
    }

    public boolean isHatUnlocked() {
        return hatUnlocked;
    }

    public void setHatUnlocked(boolean hatUnlocked) {
        this.hatUnlocked = hatUnlocked;
    }

    public boolean isFeedUnlocked() {
        return feedUnlocked;
    }

    public void setFeedUnlocked(boolean feedUnlocked) {
        this.feedUnlocked = feedUnlocked;
    }

    // ==================== AMÉLIORATIONS D'ÎLE (/upgrades, argent) ====================

    public int getUpgradeLevel(String key) {
        return upgradeLevels.getOrDefault(key, 0);
    }

    public void setUpgradeLevel(String key, int level) {
        upgradeLevels.put(key, Math.max(0, level));
    }

    public Map<String, Integer> getUpgradeLevels() {
        return upgradeLevels;
    }

    // ==================== BOUTIQUE DE RENAISSANCE (/rebirthshop, points) ====================

    public int getRebirthShopLevel(String key) {
        return rebirthShopLevels.getOrDefault(key, 0);
    }

    public void setRebirthShopLevel(String key, int level) {
        rebirthShopLevels.put(key, Math.max(0, level));
    }

    public Map<String, Integer> getRebirthShopLevels() {
        return rebirthShopLevels;
    }

    public int getRebirthPoints() {
        return rebirthPoints;
    }

    public void setRebirthPoints(int rebirthPoints) {
        this.rebirthPoints = Math.max(0, rebirthPoints);
    }

    public void addRebirthPoints(int amount) {
        setRebirthPoints(this.rebirthPoints + amount);
    }

    // ==================== ÉQUIPE ====================

    public Set<UUID> getMembers() {
        return members;
    }

    /** true si l'uuid est le propriétaire OU un coéquipier de cette île. */
    public boolean isOwnerOrMember(UUID uuid) {
        return owner.equals(uuid) || members.contains(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    /** Ajoute un coéquipier (rôle MEMBRE, permissions communes à tous les membres). */
    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    // ==================== CONFIANCE (/ob trust, /ob untrust) ====================

    public Set<UUID> getTrustedPlayers() {
        return trustedPlayers;
    }

    public boolean isTrusted(UUID uuid) {
        return trustedPlayers.contains(uuid);
    }

    /** Ajoute (ou re-confirme) un joueur de confiance (rôle TRUST). */
    public void addTrusted(UUID uuid) {
        trustedPlayers.add(uuid);
    }

    public void removeTrusted(UUID uuid) {
        trustedPlayers.remove(uuid);
    }

    // ==================== PERMISSIONS PAR RÔLE (/ob perms, /ob permissions) ====================

    /**
     * Rôle effectif d'un joueur sur cette île : MEMBRE si coéquipier, TRUST
     * si joueur de confiance, VISITEUR sinon. Le propriétaire n'a pas de
     * rôle (voir hasPermission) : cette méthode ne doit pas être appelée
     * pour décider de son accès.
     */
    public Role getRole(UUID uuid) {
        if (isMember(uuid)) return Role.MEMBRE;
        if (isTrusted(uuid)) return Role.TRUST;
        return Role.VISITEUR;
    }

    /** Table des 36 permissions d'un rôle sur cette île (créée avec les valeurs par défaut au premier accès). */
    public Map<String, Boolean> getRolePermissions(Role role) {
        return rolePermissions.computeIfAbsent(role, IslandPermission::defaultsFor);
    }

    public boolean getRolePermission(Role role, String key) {
        IslandPermission perm = IslandPermission.byKey(key);
        boolean def = perm == null || perm.defaultFor(role);
        return getRolePermissions(role).getOrDefault(key, def);
    }

    public void setRolePermission(Role role, String key, boolean value) {
        getRolePermissions(role).put(key, value);
    }

    /**
     * Autorisation effective d'une permission précise (voir IslandPermission)
     * pour un uuid donné sur CETTE île : le propriétaire a toujours accès à
     * tout ; tout autre joueur dépend des permissions de son rôle
     * (VISITEUR / TRUST / MEMBRE), réglables par le propriétaire via /ob perms.
     */
    public boolean hasPermission(UUID uuid, String key) {
        if (owner.equals(uuid)) return true;
        return getRolePermission(getRole(uuid), key);
    }

    public boolean canBuild(UUID uuid) {
        return hasPermission(uuid, "build");
    }

    public boolean canBreak(UUID uuid) {
        return hasPermission(uuid, "break");
    }

    public boolean canUseContainers(UUID uuid) {
        return hasPermission(uuid, "containers");
    }

    /**
     * Autorisation d'utiliser la caisse d'île (/bankis) dans les deux sens :
     * déposer ET retirer de l'argent, ainsi que déposer ET retirer des
     * objets du coffre partagé (/bankis objets). Une seule permission
     * ("bank") gouverne les quatre actions - pas de permission séparée pour
     * le dépôt : un rôle qui n'a pas accès à la banque ne doit pas non plus
     * pouvoir y déposer.
     */
    public boolean canUseBank(UUID uuid) {
        return hasPermission(uuid, "bank");
    }

    /** Propriétaire, coéquipier, ou joueur de confiance : utilisé notamment pour laisser passer /ob visit malgré le verrouillage (/ob lock). */
    public boolean isOwnerMemberOrTrusted(UUID uuid) {
        return isOwnerOrMember(uuid) || isTrusted(uuid);
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Set<UUID> getBannedPlayers() {
        return bannedPlayers;
    }

    public boolean isBanned(UUID uuid) {
        return bannedPlayers.contains(uuid);
    }

    public void banPlayer(UUID uuid) {
        bannedPlayers.add(uuid);
    }

    public void unbanPlayer(UUID uuid) {
        bannedPlayers.remove(uuid);
    }
}
