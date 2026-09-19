package fr.tuto.oneblock.managers;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.gui.AfkGUI;
import fr.tuto.oneblock.gui.AfkGuiHolder;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.time.Duration;

/**
 * Zone AFK (config.yml -> afk-zone) : tant qu'un joueur se trouve dans une
 * zone rectangulaire définie en config, il accumule des récompenses (tokens,
 * argent, clés) toutes les X secondes. Les récompenses ne sont PAS données
 * immédiatement : elles s'accumulent en attente et le joueur doit les
 * récupérer via le GUI /afk (bouton "Récupérer").
 */
public class AfkZoneManager {

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final Random random = new Random();

    private boolean enabled;
    private String worldName;
    private double minX, maxX, minY, maxY, minZ, maxZ;
    private int intervalSeconds;
    private String pendingMessage;
    private boolean chatMessageEnabled;
    private boolean titleEnabled;
    private String titleText;
    private String subtitleText;
    private int tokenMin, tokenMax;
    private double moneyMin, moneyMax;
    private final List<AfkKeyDef> keys = new ArrayList<>();

    // Titre affiché EN BOUCLE toutes les timerTitleIntervalSeconds secondes tant
    // que le joueur reste dans la zone (affiche le temps restant avant la
    // prochaine récompense). Distinct du titre de récompense (titleEnabled /
    // titleText / subtitleText ci-dessus), qui ne s'affiche qu'au moment où une
    // récompense est effectivement accumulée.
    private boolean timerTitleEnabled;
    private int timerTitleIntervalSeconds;
    private String timerTitleText;
    private String timerSubtitleText;

    // Commande exécutée en console pour donner une clé de crate (plugin
    // EcCrates). {player} -> nom du joueur, {type} -> ecrates-type de la clé
    // (voir afk-zone.keys.<id>.ecrates-type dans config.yml).
    private String ecratesCommand;

    private BukkitTask task;
    private BukkitTask guiUpdateTask;

    // Timestamp (ms epoch) de la prochaine accumulation de récompenses, PAR
    // JOUEUR. Un joueur n'a une entrée ici que tant qu'il est effectivement
    // dans la zone AFK : elle est créée à son entrée et supprimée dès qu'il
    // en sort, pour que le compte à rebours ne défile QUE pour les joueurs
    // réellement présents dans la zone (et pas pour tout le monde en continu).
    private final Map<UUID, Long> nextTriggerPerPlayer = new HashMap<>();

    // Timestamp (ms epoch) du prochain affichage du titre périodique
    // (timer-title-*), PAR JOUEUR. Même principe que nextTriggerPerPlayer,
    // mais sur un intervalle indépendant (timerTitleIntervalSeconds).
    private final Map<UUID, Long> nextTimerTitlePerPlayer = new HashMap<>();

    // Récompenses en attente par joueur, en attendant qu'il les récupère avec /afk.
    // Persisté sur disque (AFKstorage.yml) pour ne pas perdre les récompenses
    // non récupérées lors d'un restart/crash du serveur : voir loadPending() /
    // savePending().
    private final Map<UUID, PendingRewards> pending = new HashMap<>();

    private File storageFile;
    private BukkitTask autoSaveTask;

    /**
     * Définition d'un type de clé configurable (afk-zone.keys.<id> dans config.yml).
     * "material" ne sert plus qu'à l'icône affichée dans le GUI /afk : la clé
     * n'est plus donnée comme item, elle est donnée via la commande ecrates
     * (voir "ecratesType", envoyé en console à la récupération).
     */
    public record AfkKeyDef(String id, double chance, String displayName, Material material, String ecratesType) {}

    /** Récompenses en attente d'un joueur : tokens, argent, et nombre de clés par type. */
    public static class PendingRewards {
        private int tokens;
        private double money;
        private final Map<String, Integer> keyCounts = new LinkedHashMap<>();

        public int getTokens() {
            return tokens;
        }

        public double getMoney() {
            return money;
        }

        public Map<String, Integer> getKeyCounts() {
            return keyCounts;
        }

        public int getKeyCount(String keyId) {
            return keyCounts.getOrDefault(keyId, 0);
        }

        public boolean isEmpty() {
            return tokens == 0 && money == 0 && keyCounts.isEmpty();
        }
    }

    public AfkZoneManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("afk-zone");
        keys.clear();
        if (section == null) {
            enabled = false;
            return;
        }

        enabled = section.getBoolean("enabled", true);
        worldName = section.getString("world", "spawn");

        double x1 = section.getDouble("corner1.x", 0);
        double y1 = section.getDouble("corner1.y", 100);
        double z1 = section.getDouble("corner1.z", 0);
        double x2 = section.getDouble("corner2.x", 0);
        double y2 = section.getDouble("corner2.y", 100);
        double z2 = section.getDouble("corner2.z", 0);
        double height = section.getDouble("height", 3);

        // +1 sur les bornes max X/Z pour inclure le bloc entier de la coordonnée
        // donnée (ex: x de -27 à -15 doit couvrir tout le bloc en x=-15).
        minX = Math.min(x1, x2);
        maxX = Math.max(x1, x2) + 1;
        minZ = Math.min(z1, z2);
        maxZ = Math.max(z1, z2) + 1;
        minY = Math.min(y1, y2);
        maxY = Math.max(y1, y2) + Math.max(height, 0);

        intervalSeconds = Math.max(1, section.getInt("interval-seconds", 300));
        pendingMessage = section.getString("reward-message",
                "&b✦ Zone AFK &7- &a+{tokens} tokens&7, &a+{money}$&7, &f{key} &7en attente. Tape &f/afk &7pour les récupérer !");
        chatMessageEnabled = section.getBoolean("chat-message-enabled", true);

        // Title affiché au centre de l'écran à chaque accumulation de récompenses
        // (en plus ou à la place du message dans le chat, voir chat-message-enabled).
        titleEnabled = section.getBoolean("title-enabled", true);
        titleText = section.getString("title-text", "&b✦ Zone AFK");
        subtitleText = section.getString("subtitle-text",
                "&a+{tokens} tokens &7- &a+{money}$ &7- &f{key}");

        timerTitleEnabled = section.getBoolean("timer-title-enabled", true);
        timerTitleIntervalSeconds = Math.max(1, section.getInt("timer-title-interval-seconds", 5));
        timerTitleText = section.getString("timer-title-text", "&b✦ Zone AFK");
        timerSubtitleText = section.getString("timer-subtitle-text",
                "&7Prochaine récompense dans &f{time}");

        tokenMin = section.getInt("tokens.min", 1);
        tokenMax = Math.max(tokenMin, section.getInt("tokens.max", tokenMin));

        moneyMin = section.getDouble("money.min", 1);
        moneyMax = Math.max(moneyMin, section.getDouble("money.max", moneyMin));

        ecratesCommand = section.getString("ecrates-command", "ecrates key give {player} {type}");

        ConfigurationSection keysSection = section.getConfigurationSection("keys");
        if (keysSection != null) {
            for (String id : keysSection.getKeys(false)) {
                ConfigurationSection keySection = keysSection.getConfigurationSection(id);
                if (keySection == null) continue;
                double chance = keySection.getDouble("chance", 0);
                String displayName = keySection.getString("display-name", "&fClé");
                Material material;
                try {
                    material = Material.valueOf(keySection.getString("material", "TRIPWIRE_HOOK").toUpperCase());
                } catch (IllegalArgumentException e) {
                    material = Material.TRIPWIRE_HOOK;
                }
                // Type de clé côté ecrates (ex: common, magical, legendary,
                // oneblock). Par défaut on retombe sur l'id de la clé.
                String ecratesType = keySection.getString("ecrates-type", id);
                keys.add(new AfkKeyDef(id, chance, displayName, material, ecratesType));
            }
        }
    }

    /** (Re)démarre la tâche de distribution périodique (appelé une fois au démarrage du plugin). */
    public void start() {
        stop();
        loadPending();
        if (!enabled) return;
        nextTriggerPerPlayer.clear();
        nextTimerTitlePerPlayer.clear();
        // Vérifie chaque seconde qui est dans la zone : c'est ce qui permet à
        // chaque joueur d'avoir SON PROPRE compte à rebours, démarré au moment
        // où il entre réellement dans la zone (et arrêté quand il en sort).
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        // Rafraîchit le compte à rebours affiché dans le GUI /afk (si ouvert) chaque seconde.
        guiUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateOpenGuis, 20L, 20L);
        // Sauvegarde périodique de secours (toutes les 5 minutes) : couvre le
        // cas d'un crash / kill du serveur (pas de onDisable), où les
        // récompenses en attente seraient sinon perdues jusqu'à la dernière
        // sauvegarde. La sauvegarde "officielle" reste celle faite dans
        // onDisable() à chaque arrêt propre du plugin.
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::savePending, 6000L, 6000L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (guiUpdateTask != null) {
            guiUpdateTask.cancel();
            guiUpdateTask = null;
        }
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        savePending();
        nextTriggerPerPlayer.clear();
        nextTimerTitlePerPlayer.clear();
    }

    // ==================== Persistance AFKstorage.yml ====================

    private File getStorageFile() {
        if (storageFile == null) {
            storageFile = new File(plugin.getDataFolder(), "AFKstorage.yml");
        }
        return storageFile;
    }

    /**
     * Charge les récompenses en attente depuis AFKstorage.yml (appelé au
     * démarrage du plugin, avant de relancer la tâche de distribution).
     * Si le fichier n'existe pas encore (première installation / aucune
     * récompense jamais accumulée), ne fait rien.
     */
    private void loadPending() {
        pending.clear();
        File file = getStorageFile();
        if (!file.exists()) return;

        FileConfiguration storage = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playersSection = storage.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidStr);
            if (playerSection == null) continue;

            PendingRewards rewards = new PendingRewards();
            rewards.tokens = playerSection.getInt("tokens", 0);
            rewards.money = playerSection.getDouble("money", 0);
            ConfigurationSection keysSection = playerSection.getConfigurationSection("keys");
            if (keysSection != null) {
                for (String keyId : keysSection.getKeys(false)) {
                    rewards.keyCounts.put(keyId, keysSection.getInt(keyId, 0));
                }
            }
            if (!rewards.isEmpty()) {
                pending.put(uuid, rewards);
            }
        }

        plugin.getLogger().info("Zone AFK : " + pending.size()
                + " joueur(s) avec des récompenses en attente rechargées depuis AFKstorage.yml.");
    }

    /**
     * Sauvegarde les récompenses en attente dans AFKstorage.yml : appelé à
     * l'arrêt du plugin (onDisable / stop()) et périodiquement en tâche de
     * fond, pour ne jamais perdre les récompenses non récupérées d'un joueur
     * en cas de restart ou de crash du serveur.
     */
    public synchronized void savePending() {
        FileConfiguration storage = new YamlConfiguration();
        ConfigurationSection playersSection = storage.createSection("players");

        for (Map.Entry<UUID, PendingRewards> entry : pending.entrySet()) {
            PendingRewards rewards = entry.getValue();
            if (rewards.isEmpty()) continue;

            ConfigurationSection playerSection = playersSection.createSection(entry.getKey().toString());
            playerSection.set("tokens", rewards.tokens);
            playerSection.set("money", rewards.money);
            if (!rewards.keyCounts.isEmpty()) {
                ConfigurationSection keysSection = playerSection.createSection("keys");
                for (Map.Entry<String, Integer> keyEntry : rewards.keyCounts.entrySet()) {
                    keysSection.set(keyEntry.getKey(), keyEntry.getValue());
                }
            }
        }

        try {
            storage.save(getStorageFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder AFKstorage.yml : " + e.getMessage());
        }
    }

    private void tick() {
        if (!enabled) return;

        long now = System.currentTimeMillis();

        // On parcourt TOUS les joueurs connectés (et non plus seulement
        // Bukkit.getWorld(worldName).getPlayers()) : si "world" dans la config
        // ne correspond à aucun monde actuellement chargé (faute de frappe,
        // monde renommé, pas encore chargé au démarrage...), l'ancienne version
        // s'arrêtait ici pour TOUT LE MONDE, en silence, et la zone AFK ne
        // fonctionnait plus jamais pour personne. isInZone() vérifie déjà le
        // monde du joueur ci-dessous, donc ce filtre était redondant.
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            if (!isInZone(player.getLocation())) {
                // Pas (ou plus) dans la zone : on retire ses timers perso, ils
                // ne doivent plus défiler tant qu'il n'y est pas revenu.
                nextTriggerPerPlayer.remove(uuid);
                nextTimerTitlePerPlayer.remove(uuid);
                continue;
            }

            Long next = nextTriggerPerPlayer.get(uuid);
            if (next == null) {
                // Vient d'entrer dans la zone : démarre son propre compte à rebours.
                next = now + intervalSeconds * 1000L;
                nextTriggerPerPlayer.put(uuid, next);
            } else if (now >= next) {
                accumulateRewards(player);
                next = now + intervalSeconds * 1000L;
                nextTriggerPerPlayer.put(uuid, next);
            }

            // Titre périodique (timer-title-*) : sans lui, un joueur qui vient
            // d'entrer dans la zone n'a AUCUN retour visuel avant la première
            // récompense (jusqu'à interval-seconds, potentiellement plusieurs
            // minutes) et peut légitimement croire que la zone AFK ne
            // fonctionne pas. On l'affiche donc en boucle toutes les
            // timerTitleIntervalSeconds secondes, indépendamment du compte à
            // rebours de récompense.
            if (timerTitleEnabled) {
                Long nextTitle = nextTimerTitlePerPlayer.get(uuid);
                if (nextTitle == null || now >= nextTitle) {
                    long secondsLeft = Math.max(0, (next - now) / 1000);
                    Component title = legacy.deserialize(timerTitleText);
                    Component subtitle = legacy.deserialize(timerSubtitleText
                            .replace("{time}", formatSeconds(secondsLeft)));
                    player.showTitle(Title.title(title, subtitle,
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1400), Duration.ofMillis(300))));
                    nextTimerTitlePerPlayer.put(uuid, now + timerTitleIntervalSeconds * 1000L);
                }
            }
        }
    }

    /** Formate un nombre de secondes en "Xm Ys" (ou "Xs" en dessous d'une minute), pour {time}. */
    private String formatSeconds(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    /**
     * Secondes restantes avant la prochaine accumulation de récompenses pour
     * CE joueur (pour le GUI /afk). Retourne -1 si le joueur n'est pas
     * actuellement dans la zone AFK (son compte à rebours ne défile pas).
     */
    public long getSecondsUntilNextReward(UUID uuid) {
        Long next = nextTriggerPerPlayer.get(uuid);
        if (next == null) return -1;
        return Math.max(0, (next - System.currentTimeMillis()) / 1000);
    }

    /** Met à jour uniquement l'item "compte à rebours" du GUI /afk pour tout joueur qui l'a ouvert. */
    private void updateOpenGuis() {
        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof AfkGuiHolder) {
                AfkGUI.updateCountdown(top, getSecondsUntilNextReward(player.getUniqueId()));
            }
        }
    }

    /** true si la location est dans la zone AFK (monde + boîte X/Y/Z). */
    public boolean isInZone(Location loc) {
        if (!enabled || loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * Diagnostic humainement lisible pour /afk debug : compare la position
     * actuelle du joueur à la zone configurée, ligne par ligne, pour repérer
     * en un coup d'œil ce qui ne correspond pas (mauvais monde le plus souvent,
     * sinon coordonnées hors zone).
     */
    public List<String> debugInfo(Location loc) {
        List<String> lines = new ArrayList<>();
        lines.add("&7Zone activée: " + (enabled ? "&aoui" : "&cnon"));
        lines.add("&7Monde configuré: &f" + worldName + " &7- &7Ton monde: &f"
                + (loc.getWorld() != null ? loc.getWorld().getName() : "?")
                + (loc.getWorld() != null && loc.getWorld().getName().equals(worldName) ? " &a✔" : " &c✘ (ne correspond pas !)"));
        lines.add("&7Zone X: &f" + trimNumber(minX) + " → " + trimNumber(maxX)
                + " &7- Toi: &f" + trimNumber(loc.getX())
                + (loc.getX() >= minX && loc.getX() <= maxX ? " &a✔" : " &c✘"));
        lines.add("&7Zone Y: &f" + trimNumber(minY) + " → " + trimNumber(maxY)
                + " &7- Toi: &f" + trimNumber(loc.getY())
                + (loc.getY() >= minY && loc.getY() <= maxY ? " &a✔" : " &c✘"));
        lines.add("&7Zone Z: &f" + trimNumber(minZ) + " → " + trimNumber(maxZ)
                + " &7- Toi: &f" + trimNumber(loc.getZ())
                + (loc.getZ() >= minZ && loc.getZ() <= maxZ ? " &a✔" : " &c✘"));
        lines.add(isInZone(loc) ? "&a✔ Tu es actuellement DANS la zone AFK." : "&c✘ Tu n'es PAS dans la zone AFK.");
        return lines;
    }

    private static String trimNumber(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.format("%.1f", value);
    }

    public List<AfkKeyDef> getKeyDefinitions() {
        return Collections.unmodifiableList(keys);
    }

    /**
     * Donne "amount" clés du type ecrates "ecratesType" au joueur via la
     * commande ecrates configurée (ecrates-command). Utilisé notamment par
     * la boutique à tokens (/token) pour vendre des clés à l'unité.
     */
    public void giveEcratesKey(Player player, String ecratesType, int amount) {
        String cmdTemplate = ecratesCommand
                .replace("{player}", player.getName())
                .replace("{type}", ecratesType);
        for (int i = 0; i < amount; i++) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdTemplate);
        }
    }

    /** Récompenses en attente d'un joueur (jamais null, vide si aucune). */
    public PendingRewards getPending(UUID uuid) {
        return pending.getOrDefault(uuid, new PendingRewards());
    }

    private void accumulateRewards(Player player) {
        int tokenAmount = tokenMin + random.nextInt(tokenMax - tokenMin + 1);
        long moneyAmount = Math.round(moneyMin + random.nextDouble() * (moneyMax - moneyMin));
        AfkKeyDef wonKey = pickKey();

        PendingRewards rewards = pending.computeIfAbsent(player.getUniqueId(), k -> new PendingRewards());
        rewards.tokens += tokenAmount;
        rewards.money += moneyAmount;
        if (wonKey != null) {
            rewards.keyCounts.merge(wonKey.id(), 1, Integer::sum);
        }

        String keyName = wonKey != null ? wonKey.displayName() : "&7(aucune clé)";

        if (chatMessageEnabled) {
            String text = pendingMessage
                    .replace("{tokens}", String.valueOf(tokenAmount))
                    .replace("{money}", String.valueOf(moneyAmount))
                    .replace("{key}", keyName);
            player.sendMessage(legacy.deserialize(text));
        }

        if (titleEnabled) {
            Component title = legacy.deserialize(titleText);
            Component subtitle = legacy.deserialize(subtitleText
                    .replace("{tokens}", String.valueOf(tokenAmount))
                    .replace("{money}", String.valueOf(moneyAmount))
                    .replace("{key}", keyName));
            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500))));
        }
    }

    /**
     * Récupère (donne réellement) toutes les récompenses en attente d'un joueur :
     * tokens sur la caisse à tokens de son île, argent sur son compte Vault, et
     * les clés dans son inventaire. Retourne false s'il n'y avait rien à récupérer.
     */
    public boolean claim(Player player) {
        UUID uuid = player.getUniqueId();
        PendingRewards rewards = pending.get(uuid);
        if (rewards == null || rewards.isEmpty()) return false;

        if (rewards.tokens > 0) {
            IslandData island = plugin.getManager().getIsland(uuid);
            if (island != null) {
                // Crédite le solde PERSONNEL du joueur qui récupère, pas toute l'île.
                island.addTokens(uuid, rewards.tokens);
            }
        }

        if (rewards.money > 0 && plugin.getVaultManager().isEnabled()) {
            plugin.getVaultManager().deposit(player, rewards.money);
        }

        for (Map.Entry<String, Integer> entry : rewards.keyCounts.entrySet()) {
            AfkKeyDef def = findKeyDef(entry.getKey());
            if (def == null) continue;
            giveKeys(player, def, entry.getValue());
        }

        pending.remove(uuid);
        // Sauvegarde immédiate : évite que le fichier AFKstorage.yml garde
        // des récompenses déjà données si le serveur crash juste après une
        // récupération (avant la prochaine sauvegarde périodique).
        savePending();
        return true;
    }

    private AfkKeyDef findKeyDef(String id) {
        for (AfkKeyDef def : keys) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    /**
     * Donne "amount" clés du type "def" au joueur via la commande ecrates
     * (exécutée en console), une fois par clé (la commande /ecrates key give
     * ne prend pas de quantité). Doit être appelé depuis le thread principal.
     */
    private void giveKeys(Player player, AfkKeyDef def, int amount) {
        giveEcratesKey(player, def.ecratesType(), amount);
    }

    /**
     * Tire une clé au sort selon les pourcentages "chance" configurés (ex:
     * 70/20/7/3). La somme n'a pas besoin de faire exactement 100 : le tirage
     * est normalisé sur le total réel des poids configurés.
     */
    private AfkKeyDef pickKey() {
        if (keys.isEmpty()) return null;
        double total = keys.stream().mapToDouble(AfkKeyDef::chance).sum();
        if (total <= 0) return null;

        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (AfkKeyDef key : keys) {
            cumulative += key.chance();
            if (roll <= cumulative) return key;
        }
        return keys.get(keys.size() - 1);
    }
}
