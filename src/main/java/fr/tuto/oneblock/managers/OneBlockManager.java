package fr.tuto.oneblock.managers;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.gui.BankisItemsGUI;
import fr.tuto.oneblock.models.IslandData;
import fr.tuto.oneblock.models.LootItem;
import fr.tuto.oneblock.models.OneBlockLevel;
import fr.tuto.oneblock.models.ShopUpgrade;
import fr.tuto.oneblock.world.VoidGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OneBlockManager {

    // ==================== BLOCS "PLEINS" DU NETHER / DE L'END ====================
    // Listes utilisées pour la condition de renaissance Nether/End (voir
    // isNetherRebirthReady/isEndRebirthReady ci-dessous) : le joueur doit avoir
    // obtenu au moins une fois CHAQUE bloc de la liste correspondante, ce qui
    // exclut volontairement les blocs qui ne sont pas des blocs "pleins" (pas
    // un cube complet) comme les torches, pousses, lianes, plaques de pression,
    // portails, fleurs, champignons non-blocs, etc.
    public static final Set<Material> NETHER_FULL_BLOCKS = java.util.Collections.unmodifiableSet(EnumSet.of(
            Material.NETHERRACK,
            Material.NETHER_QUARTZ_ORE,
            Material.NETHER_GOLD_ORE,
            Material.MAGMA_BLOCK,
            Material.SOUL_SAND,
            Material.SOUL_SOIL,
            Material.BASALT,
            Material.POLISHED_BASALT,
            Material.BLACKSTONE,
            Material.POLISHED_BLACKSTONE,
            Material.GILDED_BLACKSTONE,
            Material.CRIMSON_NYLIUM,
            Material.WARPED_NYLIUM,
            Material.NETHER_WART_BLOCK,
            Material.WARPED_WART_BLOCK,
            Material.CRIMSON_STEM,
            Material.WARPED_STEM,
            Material.SHROOMLIGHT,
            Material.ANCIENT_DEBRIS,
            Material.GLOWSTONE,
            Material.BONE_BLOCK,
            Material.NETHER_BRICKS,
            Material.RED_NETHER_BRICKS,
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN
    ));

    public static final Set<Material> END_FULL_BLOCKS = java.util.Collections.unmodifiableSet(EnumSet.of(
            Material.END_STONE,
            Material.END_STONE_BRICKS,
            Material.PURPUR_BLOCK,
            Material.PURPUR_PILLAR,
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN
    ));

    private final OneBlockPlugin plugin;
    private final List<OneBlockLevel> levels = new ArrayList<>();
    private final Map<UUID, IslandData> islands = new HashMap<>();
    // Coffres partagés de la caisse d'île (/bankis objets), un seul Inventory vivant par
    // île (clé = UUID du propriétaire), créé à la demande et réutilisé pour toute la
    // durée de vie du plugin : voir getOrCreateBankInventory().
    private final Map<UUID, Inventory> bankInventories = new HashMap<>();
    // Tâches d'affichage de la bordure en particules (une par joueur ayant le réglage activé)
    private final Map<UUID, BukkitTask> borderParticleTasks = new HashMap<>();

    // ==================== ÉQUIPES (/ob team, /ob kick, /ob ban, /ob permissions, /ob leave) ====================

    // Index inverse : uuid d'un coéquipier -> uuid du propriétaire de l'île qu'il a rejointe.
    // Reconstruit à chaque chargement/modification pour que getIsland()/hasIsland() résolvent
    // aussi bien le propriétaire que ses coéquipiers, en O(1).
    private final Map<UUID, UUID> memberIndex = new HashMap<>();
    // Invitations en attente : uuid du joueur invité -> uuid du propriétaire qui invite.
    // Expirent automatiquement après INVITE_EXPIRY_TICKS.
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();
    private static final long INVITE_EXPIRY_TICKS = 20L * 60; // 60 secondes

    private int islandY;
    private Material fillerBlock;

    // ==================== COFFRES BONUS (loot commun, sans objet rare) ====================
    private boolean chestLootEnabled;
    private double chestLootChance; // en %
    private int chestSlotsMin;
    private int chestSlotsMax;
    private final List<ChestLootEntry> chestLootItems = new ArrayList<>();
    private static final Random CHEST_RANDOM = new Random();

    private boolean clearEnabled;
    private int clearRadius;
    private boolean clearToVoid;
    private int clearBelow;
    private int clearAbove;

    private int borderDefaultSize;
    private int borderExpandAmount;
    private int borderMaxSize;
    private double borderExpandCost;
    private int borderWarningDistance;

    private double bankLevelupBaseReward;
    private String bankLevelupMessage;

    private boolean rebirthEnabled;
    private int rebirthRequiredLevel;
    private double rebirthMoneyReward;
    // Seuil de blocs cassés (Nether OU End) permettant de renaître sur cette
    // dimension même sans avoir atteint le niveau requis en overworld (voir
    // isNetherRebirthReady / isEndRebirthReady). Valeur par défaut : 1000.
    private int rebirthNetherEndRequiredBlocks;

    private int topSize;

    private int tokenFlyCost;

    // Améliorations d'île achetées avec de l'argent (/upgrades)
    private final Map<String, ShopUpgrade> upgrades = new LinkedHashMap<>();
    // Améliorations achetées avec des points de renaissance (/rebirthshop)
    private final Map<String, ShopUpgrade> rebirthShopItems = new LinkedHashMap<>();
    private int rebirthShopPointsPerRebirth;

    private final Map<String, Boolean> defaultSettings = new HashMap<>();

    private File islandsFile;

    private String worldPrefix;

    public OneBlockManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== CHARGEMENT CONFIG ====================

    public void loadConfig() {
        plugin.reloadConfig();
        plugin.reloadRebirthConfig();
        plugin.reloadTokenConfig();
        FileConfiguration cfg = plugin.getConfig();
        FileConfiguration rebirthCfg = plugin.getRebirthConfig();
        FileConfiguration tokenCfg = plugin.getTokenConfig();

        worldPrefix = cfg.getString("island-world-prefix", "is_de_");
        islandY = cfg.getInt("island-y", 100);

        clearEnabled = cfg.getBoolean("island-clear.enabled", true);
        clearRadius = cfg.getInt("island-clear.radius", 25);
        clearToVoid = cfg.getBoolean("island-clear.clear-to-void", true);
        clearBelow = cfg.getInt("island-clear.height-below", 15);
        clearAbove = cfg.getInt("island-clear.height-above", 40);
        fillerBlock = Material.matchMaterial(cfg.getString("filler-block", "STONE"));
        if (fillerBlock == null) fillerBlock = Material.STONE;

        borderDefaultSize = cfg.getInt("border.default-size", 50);
        borderExpandAmount = cfg.getInt("border.expand-amount", 10);
        borderMaxSize = cfg.getInt("border.max-size", -1);
        borderExpandCost = cfg.getDouble("border.expand-cost", 500);
        borderWarningDistance = cfg.getInt("border.warning-distance", 3);

        bankLevelupBaseReward = cfg.getDouble("bank.levelup-base-reward", 25);
        bankLevelupMessage = cfg.getString("bank.levelup-message", "&a+{amount}$ &7dans la caisse d'île !");

        // rebirth.enabled/required-level/money-reward sont désormais dans rebirth.yml
        rebirthEnabled = rebirthCfg.getBoolean("rebirth.enabled", true);
        rebirthRequiredLevel = rebirthCfg.getInt("rebirth.required-level", 9);
        rebirthMoneyReward = rebirthCfg.getDouble("rebirth.money-reward", 1000);
        rebirthNetherEndRequiredBlocks = rebirthCfg.getInt("rebirth.nether-end-required-blocks", 1000);

        topSize = Math.max(1, cfg.getInt("top.size", 10));

        // token.fly-cost est désormais dans token.yml
        tokenFlyCost = tokenCfg.getInt("token.fly-cost", 1000);

        upgrades.clear();
        ConfigurationSection upgradesSection = cfg.getConfigurationSection("upgrades");
        if (upgradesSection != null) {
            for (String key : upgradesSection.getKeys(false)) {
                ConfigurationSection sec = upgradesSection.getConfigurationSection(key);
                if (sec == null) continue;
                upgrades.put(key, parseShopUpgrade(key, sec));
            }
        }

        // rebirth-shop (boutique de renaissance) est désormais dans rebirth.yml
        rebirthShopItems.clear();
        rebirthShopPointsPerRebirth = rebirthCfg.getInt("rebirth-shop.points-per-rebirth", 1);
        ConfigurationSection rebirthShopSection = rebirthCfg.getConfigurationSection("rebirth-shop.items");
        if (rebirthShopSection != null) {
            for (String key : rebirthShopSection.getKeys(false)) {
                ConfigurationSection sec = rebirthShopSection.getConfigurationSection(key);
                if (sec == null) continue;
                rebirthShopItems.put(key, parseShopUpgrade(key, sec));
            }
        }

        defaultSettings.clear();
        ConfigurationSection defSec = cfg.getConfigurationSection("default-settings");
        if (defSec != null) {
            for (String key : defSec.getKeys(false)) {
                defaultSettings.put(key, defSec.getBoolean(key, true));
            }
        }

        levels.clear();
        ConfigurationSection levelsSection = cfg.getConfigurationSection("levels");
        if (levelsSection == null) {
            plugin.getLogger().warning("Aucune section 'levels' trouvée dans config.yml !");
            return;
        }

        for (String key : levelsSection.getKeys(false)) {
            ConfigurationSection lvl = levelsSection.getConfigurationSection(key);
            if (lvl == null) continue;

            int id = Integer.parseInt(key);
            String name = lvl.getString("name", "Niveau " + id);
            int min = lvl.getInt("min-blocks", 0);
            int max = lvl.getInt("max-blocks", -1);

            List<LootItem> loot = new ArrayList<>();
            List<Map<?, ?>> lootList = lvl.getMapList("loot");
            for (Map<?, ?> raw : lootList) {
                String matName = String.valueOf(raw.get("material"));
                Material mat = Material.matchMaterial(matName);
                if (mat == null) {
                    plugin.getLogger().warning("Matériau inconnu dans la config: " + matName);
                    continue;
                }
                int weight = raw.containsKey("weight") ? Integer.parseInt(String.valueOf(raw.get("weight"))) : 1;
                EntityType entity = null;
                if (mat == Material.SPAWNER && raw.containsKey("entity")) {
                    try {
                        entity = EntityType.valueOf(String.valueOf(raw.get("entity")).toUpperCase());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Type d'entité inconnu: " + raw.get("entity"));
                    }
                }
                loot.add(new LootItem(mat, weight, entity));
            }

            levels.add(new OneBlockLevel(id, name, min, max, loot));
        }

        levels.sort(Comparator.comparingInt(OneBlockLevel::getId));
        plugin.getLogger().info(levels.size() + " niveaux OneBlock chargés.");

        loadChestLoot(cfg);
    }

    /**
     * Charge la config des coffres bonus ("chest-loot") : à chaque
     * régénération du bloc OneBlock, une chance (chest-loot.chance, en %)
     * qu'un coffre rempli de loot COMMUN (pousses, bâtons, bois...) apparaisse
     * à la place du bloc normalement tiré par le niveau en cours. Ce loot ne
     * contient volontairement AUCUN objet rare (pas de minerai précieux, pas
     * de spawner) : c'est un simple petit bonus de variété, pas une
     * récompense de progression.
     */
    private void loadChestLoot(FileConfiguration cfg) {
        chestLootItems.clear();
        ConfigurationSection sec = cfg.getConfigurationSection("chest-loot");
        if (sec == null) {
            chestLootEnabled = false;
            return;
        }

        chestLootEnabled = sec.getBoolean("enabled", true);
        chestLootChance = sec.getDouble("chance", 3.0);
        chestSlotsMin = Math.max(1, sec.getInt("slots.min", 1));
        chestSlotsMax = Math.max(chestSlotsMin, sec.getInt("slots.max", 3));

        List<Map<?, ?>> itemList = sec.getMapList("items");
        for (Map<?, ?> raw : itemList) {
            String matName = String.valueOf(raw.get("material"));
            Material mat = Material.matchMaterial(matName);
            if (mat == null) {
                plugin.getLogger().warning("[OneBlock] Matériau inconnu dans chest-loot.items: " + matName);
                continue;
            }
            int weight = raw.containsKey("weight") ? Integer.parseInt(String.valueOf(raw.get("weight"))) : 1;

            int amountMin = 1;
            int amountMax = 1;
            Object amountRaw = raw.get("amount");
            if (amountRaw instanceof Map<?, ?> amountMap) {
                if (amountMap.containsKey("min")) amountMin = Integer.parseInt(String.valueOf(amountMap.get("min")));
                if (amountMap.containsKey("max")) amountMax = Integer.parseInt(String.valueOf(amountMap.get("max")));
            }
            amountMin = Math.max(1, amountMin);
            amountMax = Math.max(amountMin, amountMax);

            chestLootItems.add(new ChestLootEntry(mat, Math.max(1, weight), amountMin, amountMax));
        }

        if (chestLootEnabled && chestLootItems.isEmpty()) {
            plugin.getLogger().warning("[OneBlock] chest-loot.enabled est activé mais chest-loot.items est vide : "
                    + "les coffres bonus ne pourront jamais apparaître.");
        }
    }

    /** Entrée pondérée de la loot-table des coffres bonus (voir loadChestLoot). */
    private static final class ChestLootEntry {
        final Material material;
        final int weight;
        final int amountMin;
        final int amountMax;

        ChestLootEntry(Material material, int weight, int amountMin, int amountMax) {
            this.material = material;
            this.weight = weight;
            this.amountMin = amountMin;
            this.amountMax = amountMax;
        }
    }

    /**
     * Tire au sort si un coffre bonus doit apparaître à la place du bloc
     * OneBlock normal, à chaque régénération (voir regenerateBlock()).
     */
    private boolean rollChestChance() {
        if (!chestLootEnabled || chestLootItems.isEmpty() || chestLootChance <= 0) return false;
        return CHEST_RANDOM.nextDouble() * 100.0 < chestLootChance;
    }

    /**
     * Remplit le coffre donné avec un tirage aléatoire de loot commun
     * (nombre d'emplacements aléatoire entre chest-loot.slots.min/max,
     * chaque emplacement tiré selon les poids de chest-loot.items). Aucun
     * objet rare n'est jamais présent dans cette table, par construction.
     */
    private void fillChestLoot(org.bukkit.block.Chest chest) {
        int totalWeight = 0;
        for (ChestLootEntry entry : chestLootItems) totalWeight += entry.weight;
        if (totalWeight <= 0) return;

        int slotCount = chestSlotsMin + CHEST_RANDOM.nextInt(chestSlotsMax - chestSlotsMin + 1);
        org.bukkit.inventory.Inventory inv = chest.getBlockInventory();
        int inventorySize = inv.getSize();

        for (int i = 0; i < slotCount; i++) {
            int roll = CHEST_RANDOM.nextInt(totalWeight);
            int cumulative = 0;
            ChestLootEntry chosen = chestLootItems.get(chestLootItems.size() - 1);
            for (ChestLootEntry entry : chestLootItems) {
                cumulative += entry.weight;
                if (roll < cumulative) {
                    chosen = entry;
                    break;
                }
            }

            int amount = chosen.amountMin + CHEST_RANDOM.nextInt(chosen.amountMax - chosen.amountMin + 1);
            org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(chosen.material, amount);

            int slot = CHEST_RANDOM.nextInt(inventorySize);
            // On évite d'écraser un emplacement déjà rempli lors de ce tirage
            // (sinon un item pourrait en remplacer un autre silencieusement) :
            // on cherche le prochain emplacement libre en partant de là.
            int attempts = 0;
            while (inv.getItem(slot) != null && attempts < inventorySize) {
                slot = (slot + 1) % inventorySize;
                attempts++;
            }
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, stack);
            }
        }
        // force=true : on force l'écriture même si le serveur pense que le
        // type du bloc a changé entre-temps (voir le correctif ci-dessus,
        // qui relit désormais l'état un tick plus tard pour éviter ce genre
        // de désynchronisation).
        chest.update(true, false);
    }

    // ==================== MONDES ONEBLOCK (UN PAR ÎLE) ====================

    /**
     * Chaque île possède désormais son PROPRE monde dédié (et non plus un
     * seul monde "oneblock_world" partagé où les îles sont espacées par
     * island-spacing). Le nom du monde est {island-world-prefix}{pseudo du
     * joueur}, ex: "is_de_Steve". Chaque monde est généré vide via
     * VoidGenerator, et l'île y est toujours posée en (0, island-y, 0)
     * puisqu'elle est seule dans son monde (plus besoin d'espacement).
     */
    public void initWorld() {
        // Les mondes ne sont plus créés globalement au démarrage : chacun
        // est créé/chargé à la demande (première visite de /ob start, ou
        // rechargement d'une île existante dans loadIslands()).
        plugin.getLogger().info("Mondes OneBlock : un monde dédié par île (préfixe '" + worldPrefix + "').");
    }

    /** Nettoie un pseudo pour qu'il soit un nom de dossier de monde valide. */
    private String sanitizeWorldPart(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    /**
     * Nom du monde dédié à l'île d'un joueur, ex: "is_de_Steve". Si le
     * pseudo n'est pas disponible (joueur jamais vu), on retourne son UUID à
     * la place pour garder un nom stable.
     */
    public String getIslandWorldName(UUID uuid, String playerName) {
        String base = (playerName != null && !playerName.isBlank()) ? playerName : uuid.toString();
        return worldPrefix + sanitizeWorldPart(base);
    }

    /**
     * Charge le monde s'il existe déjà sur le disque, ou le crée (100% vide,
     * via VoidGenerator) s'il n'existe pas encore.
     */
    public World getOrCreateIslandWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(new VoidGenerator());
            creator.generateStructures(false);
            creator.environment(World.Environment.NORMAL);
            world = creator.createWorld();
            plugin.getLogger().info("Monde OneBlock '" + worldName + "' créé (vide).");
        }
        if (world != null) {
            world.setSpawnFlags(false, false);
            world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        }
        return world;
    }

    // ==================== MONDE NETHER PAR ÎLE (portail) ====================

    /** Nom du monde Nether dédié à l'île d'un joueur, ex: "is_nether_de_Steve". */
    public String getNetherWorldName(UUID uuid, String playerName) {
        String base = (playerName != null && !playerName.isBlank()) ? playerName : uuid.toString();
        return "is_nether_de_" + sanitizeWorldPart(base);
    }

    /** Nom du monde End dédié à l'île d'un joueur, ex: "is_end_de_Steve". */
    public String getEndWorldName(UUID uuid, String playerName) {
        String base = (playerName != null && !playerName.isBlank()) ? playerName : uuid.toString();
        return "is_end_de_" + sanitizeWorldPart(base);
    }

    public World getOrCreateNetherWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(new VoidGenerator());
            creator.generateStructures(false);
            creator.environment(World.Environment.NETHER);
            world = creator.createWorld();
            plugin.getLogger().info("Monde Nether OneBlock '" + worldName + "' créé (vide).");
        }
        if (world != null) {
            world.setSpawnFlags(false, false);
            world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        }
        return world;
    }

    public World getOrCreateEndWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(new VoidGenerator());
            creator.generateStructures(false);
            creator.environment(World.Environment.THE_END);
            world = creator.createWorld();
            plugin.getLogger().info("Monde End OneBlock '" + worldName + "' créé (vide).");
        }
        if (world != null) {
            world.setSpawnFlags(false, false);
            world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        }
        return world;
    }

    /** Y de sécurité pour poser le bloc, en tenant compte des limites du monde (Nether/End n'ont pas forcément la même hauteur que l'overworld). */
    private int safeIslandY(World world) {
        int y = islandY;
        y = Math.max(world.getMinHeight() + 5, y);
        y = Math.min(world.getMaxHeight() - 5, y);
        return y;
    }

    /**
     * Crée (si absent) le OneBlock du monde Nether de cette île, à la
     * première entrée d'un joueur dans ce monde. Ne fait rien si déjà
     * initialisé (voir IslandData#hasNetherIsland).
     */
    public void initNetherIslandIfAbsent(IslandData island, World netherWorld) {
        if (island.hasNetherIsland()) return;

        int x = 0, z = 0;
        int y = safeIslandY(netherWorld);
        clearArea(netherWorld, x, z);

        Location bedrockLoc = new Location(netherWorld, x, y, z);
        Location blockLoc = new Location(netherWorld, x, y + 1, z);
        bedrockLoc.getBlock().setType(Material.BEDROCK);

        Material startMaterial = Material.NETHERRACK;
        blockLoc.getBlock().setType(startMaterial);

        island.setNetherBlockLocation(blockLoc);
        island.setNetherCurrentMaterial(startMaterial);
        island.recordNetherMined(startMaterial);
    }

    /**
     * Crée (si absent) le OneBlock du monde End de cette île, à la première
     * entrée d'un joueur dans ce monde.
     */
    public void initEndIslandIfAbsent(IslandData island, World endWorld) {
        if (island.hasEndIsland()) return;

        int x = 0, z = 0;
        int y = safeIslandY(endWorld);
        clearArea(endWorld, x, z);

        Location bedrockLoc = new Location(endWorld, x, y, z);
        Location blockLoc = new Location(endWorld, x, y + 1, z);
        bedrockLoc.getBlock().setType(Material.BEDROCK);

        Material startMaterial = Material.END_STONE;
        blockLoc.getBlock().setType(startMaterial);

        island.setEndBlockLocation(blockLoc);
        island.setEndCurrentMaterial(startMaterial);
        island.recordEndMined(startMaterial);
    }

    /** Tire un bloc plein aléatoire du Nether pour régénérer le OneBlock Nether. */
    private Material rollNetherLoot() {
        List<Material> pool = new ArrayList<>(NETHER_FULL_BLOCKS);
        return pool.get(new Random().nextInt(pool.size()));
    }

    /** Tire un bloc plein aléatoire de l'End pour régénérer le OneBlock End. */
    private Material rollEndLoot() {
        List<Material> pool = new ArrayList<>(END_FULL_BLOCKS);
        return pool.get(new Random().nextInt(pool.size()));
    }

    /**
     * Régénère le OneBlock Nether de cette île après une casse : compte le
     * bloc, mémorise son type (pour la condition "tous les blocs" du
     * rebirth), puis pose un nouveau bloc au même endroit.
     */
    public void regenerateNetherBlock(IslandData island) {
        regenerateNetherBlock(island, 1);
    }

    /** Idem, avec le multiplicateur du boost staff "x2 XP" (voir BoostManager) appliqué au comptage. */
    public void regenerateNetherBlock(IslandData island, int xpMultiplier) {
        island.incrementNetherBroken(xpMultiplier);
        Material next = rollNetherLoot();
        island.setNetherCurrentMaterial(next);
        island.recordNetherMined(next);
        Location loc = island.getNetherBlockLocation();
        if (loc != null && loc.getWorld() != null) {
            loc.getBlock().setType(next);
        }
    }

    /**
     * Régénère le OneBlock End de cette île après une casse : compte le
     * bloc, mémorise son type, puis pose un nouveau bloc au même endroit.
     */
    public void regenerateEndBlock(IslandData island) {
        regenerateEndBlock(island, 1);
    }

    /** Idem, avec le multiplicateur du boost staff "x2 XP" (voir BoostManager) appliqué au comptage. */
    public void regenerateEndBlock(IslandData island, int xpMultiplier) {
        island.incrementEndBroken(xpMultiplier);
        Material next = rollEndLoot();
        island.setEndCurrentMaterial(next);
        island.recordEndMined(next);
        Location loc = island.getEndBlockLocation();
        if (loc != null && loc.getWorld() != null) {
            loc.getBlock().setType(next);
        }
    }

    public Material getFillerBlock() {
        return fillerBlock;
    }

    public int getBorderDefaultSize() {
        return borderDefaultSize;
    }

    public int getBorderExpandAmount() {
        return borderExpandAmount;
    }

    public int getBorderMaxSize() {
        return borderMaxSize;
    }

    public double getBorderExpandCost() {
        return borderExpandCost;
    }

    public Map<String, Boolean> getDefaultSettings() {
        return defaultSettings;
    }

    // ==================== BANQUE D'ÎLE ====================

    /**
     * Montant versé dans la caisse d'île quand le joueur atteint le niveau
     * donné (montée de niveau). Le montant de base est multiplié par le
     * numéro du niveau atteint, pour que les niveaux avancés rapportent
     * davantage.
     */
    public double getLevelUpBankReward(int levelId) {
        return bankLevelupBaseReward * levelId;
    }

    public String getBankLevelupMessage() {
        return bankLevelupMessage;
    }

    public int getTopSize() {
        return topSize;
    }

    // ==================== RENAISSANCE ====================

    public boolean isRebirthEnabled() {
        return rebirthEnabled;
    }

    public int getRebirthRequiredLevel() {
        return rebirthRequiredLevel;
    }

    public double getRebirthMoneyReward() {
        return rebirthMoneyReward;
    }

    public int getRebirthNetherEndRequiredBlocks() {
        return rebirthNetherEndRequiredBlocks;
    }

    /**
     * Île éligible à une renaissance NETHER : au moins {@code rebirthNetherEndRequiredBlocks}
     * blocs cassés dans son Nether ET tous les blocs pleins du Nether (voir
     * NETHER_FULL_BLOCKS) obtenus au moins une fois.
     */
    public boolean isNetherRebirthReady(IslandData island) {
        return island.getNetherBrokenCount() >= rebirthNetherEndRequiredBlocks
                && island.getNetherMinedTypes().containsAll(NETHER_FULL_BLOCKS);
    }

    /**
     * Île éligible à une renaissance END : au moins {@code rebirthNetherEndRequiredBlocks}
     * blocs cassés dans son End ET tous les blocs pleins de l'End (voir
     * END_FULL_BLOCKS) obtenus au moins une fois.
     */
    public boolean isEndRebirthReady(IslandData island) {
        return island.getEndBrokenCount() >= rebirthNetherEndRequiredBlocks
                && island.getEndMinedTypes().containsAll(END_FULL_BLOCKS);
    }

    /**
     * Cherche un niveau par son identifiant (ex: pour afficher le nom du
     * niveau requis pour la renaissance).
     */
    public OneBlockLevel getLevelById(int id) {
        for (OneBlockLevel level : levels) {
            if (level.getId() == id) return level;
        }
        return null;
    }

    // ==================== TOKENS (/token) ====================

    public int getTokenFlyCost() {
        return tokenFlyCost;
    }

    // ==================== AMÉLIORATIONS D'ÎLE (/upgrades, argent) ====================

    private ShopUpgrade parseShopUpgrade(String key, ConfigurationSection sec) {
        String name = sec.getString("name", key);
        String description = sec.getString("description", "");
        Material icon = Material.matchMaterial(sec.getString("icon", "PAPER"));
        if (icon == null) icon = Material.PAPER;
        int maxLevel = Math.max(1, sec.getInt("max-level", 5));
        double baseCost = sec.getDouble("base-cost", 1000);
        double costMultiplier = sec.getDouble("cost-multiplier", 1.5);
        double bonusPerLevel = sec.getDouble("bonus-per-level", 10.0);
        boolean repeatable = sec.getBoolean("repeatable", false);
        return new ShopUpgrade(key, name, List.of(description), icon, maxLevel, baseCost, costMultiplier, bonusPerLevel, repeatable);
    }

    /** Améliorations disponibles dans /upgrades (payées en argent Vault), dans l'ordre de config.yml. */
    public Map<String, ShopUpgrade> getUpgrades() {
        return upgrades;
    }

    /** Coût (en argent, remise de /rebirthshop déjà appliquée) du prochain niveau de cette amélioration. */
    public double getUpgradeCost(IslandData island, ShopUpgrade def) {
        int level = island.getUpgradeLevel(def.key());
        double raw = def.costForLevel(level);
        return raw * getUpgradeDiscountMultiplier(island);
    }

    /**
     * Multiplicateur appliqué à l'argent versé dans la caisse d'île (montée de
     * niveau + renaissance), selon le niveau de l'amélioration "bank-boost".
     * Retourne 1.0 (aucun bonus) si cette amélioration n'existe pas en config.
     */
    public double getBankBoostMultiplier(IslandData island) {
        ShopUpgrade def = upgrades.get("bank-boost");
        if (def == null) return 1.0;
        return 1.0 + (def.bonusAtLevel(island.getUpgradeLevel("bank-boost")) / 100.0);
    }

    /**
     * Chance (en %) d'obtenir un bloc en double à chaque casse du OneBlock,
     * selon le niveau de l'amélioration "loot-boost".
     */
    public double getLootBoostChance(IslandData island) {
        ShopUpgrade def = upgrades.get("loot-boost");
        if (def == null) return 0.0;
        return def.bonusAtLevel(island.getUpgradeLevel("loot-boost"));
    }

    // ==================== BOUTIQUE DE RENAISSANCE (/rebirthshop, points) ====================

    /** Améliorations disponibles dans /rebirthshop (payées en points de renaissance). */
    public Map<String, ShopUpgrade> getRebirthShopItems() {
        return rebirthShopItems;
    }

    /** Nombre de points de renaissance gagnés à chaque utilisation de /rebirth. */
    public int getRebirthShopPointsPerRebirth() {
        return rebirthShopPointsPerRebirth;
    }

    /** Coût (en points de renaissance) du prochain niveau de cette amélioration. */
    public int getRebirthShopCost(IslandData island, ShopUpgrade def) {
        int level = island.getRebirthShopLevel(def.key());
        return (int) Math.ceil(def.costForLevel(level));
    }

    /**
     * Multiplicateur supplémentaire appliqué à la récompense d'argent d'une
     * renaissance, selon le niveau de l'amélioration "rebirth-reward-boost".
     */
    public double getRebirthRewardBoostMultiplier(IslandData island) {
        ShopUpgrade def = rebirthShopItems.get("rebirth-reward-boost");
        if (def == null) return 1.0;
        return 1.0 + (def.bonusAtLevel(island.getRebirthShopLevel("rebirth-reward-boost")) / 100.0);
    }

    /**
     * Multiplicateur de coût (entre 0 et 1) à appliquer aux prix de /upgrades,
     * selon le niveau de l'amélioration "upgrade-discount". La réduction est
     * plafonnée à 50% pour éviter les améliorations gratuites.
     */
    public double getUpgradeDiscountMultiplier(IslandData island) {
        ShopUpgrade def = rebirthShopItems.get("upgrade-discount");
        if (def == null) return 1.0;
        double discountPercent = Math.min(50.0, def.bonusAtLevel(island.getRebirthShopLevel("upgrade-discount")));
        return 1.0 - (discountPercent / 100.0);
    }

    /**
     * Indique si l'île a acheté l'amélioration "double-break" dans
     * /rebirthshop : le bloc/spawner du OneBlock est alors TOUJOURS obtenu
     * en double à chaque casse (remplace le bonus "loot-boost" qui n'est
     * qu'une chance, voir BlockBreakListener).
     */
    public boolean isDoubleBreakActive(IslandData island) {
        ShopUpgrade def = rebirthShopItems.get("double-break");
        if (def == null) return false;
        return island.getRebirthShopLevel("double-break") >= 1;
    }

    /**
     * Cherche l'île (s'il y en a une) dont la bordure contient cet emplacement.
     * Utilisé par les listeners de settings (mob-spawning, pvp, etc) ET par
     * TOUTES les vérifications de permission de rôle (build/break/mob_attack/
     * item_pickup/item_drop/containers/...), puisqu'elles passent toutes par
     * cette méthode pour retrouver l'île concernée.
     * Vérifie la bordure de l'overworld, ET celle du Nether dédié, ET celle
     * de l'End dédié de chaque île. AVANT ce correctif, seule l'End n'était
     * PAS vérifiée ici : résultat, dans la dimension End d'une île, cette
     * méthode retournait toujours null -> absolument AUCUNE protection
     * (permission de rôle, réglages d'île...) ne s'y appliquait, permettant
     * par exemple à un simple visiteur de casser/poser des blocs, taper les
     * mobs, ramasser/jeter des objets, etc. librement dans l'End de
     * n'importe quelle île. Ce correctif s'applique automatiquement à
     * TOUTES les îles (aucune donnée par île à migrer, la méthode est
     * commune à tout le plugin).
     */
    public IslandData findIslandAt(Location loc) {
        for (IslandData island : islands.values()) {
            if (island.contains(loc) || island.containsNether(loc) || island.containsEnd(loc)) return island;
        }
        return null;
    }

    /**
     * Retrouve l'île dont c'est le monde dédié, sans tenir compte des
     * limites de bordure (contrairement à findIslandAt). Utile pour
     * localiser l'île "courante" d'un joueur même lorsqu'il se trouve DÉJÀ
     * hors des limites de bordure (ex: contrôle anti-franchissement en mode
     * bordure-particules, où l'on doit justement le repousser).
     */
    public IslandData findIslandByWorld(World world) {
        if (world == null) return null;
        for (IslandData island : islands.values()) {
            World islandWorld = island.getBlockLocation().getWorld();
            if (islandWorld != null && islandWorld.equals(world)) return island;
        }
        return null;
    }

    /** Équivalent de findIslandByWorld, mais pour le monde Nether dédié d'une île. */
    public IslandData findIslandByNetherWorld(World world) {
        if (world == null) return null;
        for (IslandData island : islands.values()) {
            Location netherLoc = island.getNetherBlockLocation();
            World netherWorld = netherLoc != null ? netherLoc.getWorld() : null;
            if (netherWorld != null && netherWorld.equals(world)) return island;
        }
        return null;
    }

    /** Équivalent de findIslandByWorld, mais pour le monde End dédié d'une île. */
    public IslandData findIslandByEndWorld(World world) {
        if (world == null) return null;
        for (IslandData island : islands.values()) {
            Location endLoc = island.getEndBlockLocation();
            World endWorld = endLoc != null ? endLoc.getWorld() : null;
            if (endWorld != null && endWorld.equals(world)) return island;
        }
        return null;
    }

    /**
     * Applique (ou réapplique, ex: à la reconnexion) la WorldBorder personnelle
     * du joueur, centrée sur son île.
     *
     * Si le réglage "border-particles" de l'île est activé, la bordure
     * vanilla (le "mur" bleu/rouge du client) n'est jamais montrée au joueur :
     * on lui envoie une bordure gigantesque (donc invisible en pratique) et
     * on affiche à la place des particules rouges qui longent la limite,
     * uniquement à sa hauteur, visibles seulement par lui.
     */
    public void applyBorder(Player player, IslandData island) {
        stopBorderParticles(player);

        // La bordure doit être centrée sur le bon monde selon la dimension où
        // se trouve actuellement le joueur : son île overworld, ou son île
        // Nether (indépendante, avec son propre réglage "border-particles").
        World playerWorld = player.getWorld();
        boolean inNether = island.isNetherWorld(playerWorld);
        Location center = inNether ? island.getNetherBlockLocation() : island.getBlockLocation();
        if (center == null) center = island.getBlockLocation();

        boolean particlesMode = island.getSetting(playerWorld, "border-particles", false);

        org.bukkit.WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(center);
        if (particlesMode) {
            // Taille max autorisée par Bukkit pour une WorldBorder : en pratique
            // invisible/jamais atteinte, ce qui masque le rendu vanilla.
            border.setSize(5.9E7);
            border.setWarningDistance(0);
        } else {
            border.setSize(island.getBorderSize());
            border.setWarningDistance(borderWarningDistance);
        }
        player.setWorldBorder(border);

        if (particlesMode) {
            startBorderParticles(player, island);
        }
    }

    /**
     * Démarre la tâche répétitive qui dessine la bordure de l'île en
     * particules rouges pour ce joueur (visible uniquement par lui).
     */
    private void startBorderParticles(Player player, IslandData island) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !island.getSetting(player.getWorld(), "border-particles", false)) {
                    cancel();
                    borderParticleTasks.remove(uuid);
                    return;
                }
                drawBorderParticles(player, island);
            }
        }.runTaskTimer(plugin, 0L, 10L);
        borderParticleTasks.put(uuid, task);
    }

    /**
     * Arrête la tâche de particules de bordure d'un joueur, si elle existe
     * (déconnexion, téléportation au spawn, réglage désactivé, etc.).
     */
    public void stopBorderParticles(Player player) {
        BukkitTask task = borderParticleTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    /**
     * Dessine, uniquement autour du joueur et à sa hauteur, la portion de
     * bordure d'île la plus proche avec des particules rouges. Ne dessine
     * que les segments proches du joueur pour éviter de spammer des
     * particules sur toute la bordure si l'île est grande.
     */
    private void drawBorderParticles(Player player, IslandData island) {
        boolean inNether = island.isNetherWorld(player.getWorld());
        Location center = inNether ? island.getNetherBlockLocation() : island.getBlockLocation();
        World world = center != null ? center.getWorld() : null;
        if (world == null || !player.getWorld().equals(world)) return;

        double half = island.getBorderSize() / 2.0;
        double minX = center.getX() - half;
        double maxX = center.getX() + half;
        double minZ = center.getZ() - half;
        double maxZ = center.getZ() + half;

        Location loc = player.getLocation();
        double y = player.getEyeLocation().getY();
        double range = 24.0;
        double step = 0.5;

        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.3f);

        if (Math.abs(loc.getX() - minX) <= range) {
            drawEdgeLineX(player, minX, y, loc.getZ(), minZ, maxZ, range, step, dust);
        }
        if (Math.abs(loc.getX() - maxX) <= range) {
            drawEdgeLineX(player, maxX, y, loc.getZ(), minZ, maxZ, range, step, dust);
        }
        if (Math.abs(loc.getZ() - minZ) <= range) {
            drawEdgeLineZ(player, minZ, y, loc.getX(), minX, maxX, range, step, dust);
        }
        if (Math.abs(loc.getZ() - maxZ) <= range) {
            drawEdgeLineZ(player, maxZ, y, loc.getX(), minX, maxX, range, step, dust);
        }
    }

    // Ligne de particules le long d'un bord à X fixe (Z variable), uniquement
    // autour de la position actuelle du joueur sur cet axe.
    private void drawEdgeLineX(Player player, double x, double y, double aroundZ,
                                double minZ, double maxZ, double range, double step, Particle.DustOptions dust) {
        double from = Math.max(minZ, aroundZ - range);
        double to = Math.min(maxZ, aroundZ + range);
        for (double z = from; z <= to; z += step) {
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
        }
    }

    // Ligne de particules le long d'un bord à Z fixe (X variable), uniquement
    // autour de la position actuelle du joueur sur cet axe.
    private void drawEdgeLineZ(Player player, double z, double y, double aroundX,
                                double minX, double maxX, double range, double step, Particle.DustOptions dust) {
        double from = Math.max(minX, aroundX - range);
        double to = Math.min(maxX, aroundX + range);
        for (double x = from; x <= to; x += step) {
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * Construit la Location du spawn du serveur défini dans config.yml
     * (server-spawn.world/x/y/z). Retourne null si le monde n'existe pas.
     */
    public Location getServerSpawnLocation() {
        String worldName = plugin.getConfig().getString("server-spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = plugin.getConfig().getDouble("server-spawn.x", 0);
        double y = plugin.getConfig().getDouble("server-spawn.y", 200);
        double z = plugin.getConfig().getDouble("server-spawn.z", 0);
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    /**
     * Applique (ou retire) le blocage de l'heure sur "jour" pour ce joueur,
     * selon le réglage "always-day" de son île. Ce blocage est côté client
     * uniquement (setPlayerTime), il n'affecte pas les autres joueurs.
     */
    public void applyTimeLock(org.bukkit.entity.Player player, IslandData island) {
        boolean alwaysDay = island.getSetting(player.getWorld(), "always-day", false);
        if (alwaysDay) {
            player.setPlayerTime(6000L, false);
        } else {
            player.resetPlayerTime();
        }
    }

    // ==================== NIVEAUX ====================

    public OneBlockLevel getLevelForCount(int brokenCount) {
        for (OneBlockLevel level : levels) {
            if (level.matches(brokenCount)) return level;
        }
        // Si aucun niveau ne correspond (config incomplète), on retourne le dernier
        return levels.isEmpty() ? null : levels.get(levels.size() - 1);
    }

    public List<OneBlockLevel> getLevels() {
        return levels;
    }

    // ==================== ÎLES ====================

    /**
     * Retourne l'île de ce joueur : soit la sienne (s'il en est propriétaire), soit celle
     * de l'équipe qu'il a rejointe (s'il est coéquipier d'un autre propriétaire).
     */
    public IslandData getIsland(UUID uuid) {
        IslandData own = islands.get(uuid);
        if (own != null) return own;
        UUID ownerUuid = memberIndex.get(uuid);
        return ownerUuid != null ? islands.get(ownerUuid) : null;
    }

    public boolean hasIsland(UUID uuid) {
        return islands.containsKey(uuid) || memberIndex.containsKey(uuid);
    }

    /** true si cet uuid est propriétaire (et non simple coéquipier) d'une île. */
    public boolean isOwner(UUID uuid) {
        return islands.containsKey(uuid);
    }

    /**
     * Crée (ou recrée) une île OneBlock pour le joueur dans SON PROPRE monde
     * dédié ("{prefix}{pseudo}"), créé ou chargé au passage si besoin, et
     * renvoie l'emplacement du bloc. L'île est toujours posée en (0,
     * island-y, 0) : plus besoin d'espacement, chaque monde n'accueille
     * qu'une seule île.
     */
    public IslandData createIsland(UUID uuid, String playerName) {
        String worldName = getIslandWorldName(uuid, playerName);
        World world = getOrCreateIslandWorld(worldName);
        return createIsland(uuid, world);
    }

    /**
     * Variante bas niveau : crée l'île directement dans le monde donné (déjà
     * résolu). Utilisée en interne, et par le rechargement des îles
     * existantes au démarrage.
     */
    public IslandData createIsland(UUID uuid, World world) {
        int x = 0;
        int z = 0;

        clearArea(world, x, z);

        Location bedrockLoc = new Location(world, x, islandY, z);
        Location blockLoc = new Location(world, x, islandY + 1, z);

        bedrockLoc.getBlock().setType(Material.BEDROCK);

        OneBlockLevel firstLevel = getLevelForCount(0);
        Material startMaterial = Material.GRASS_BLOCK;
        if (firstLevel != null) {
            LootItem first = firstLevel.rollLoot();
            if (first != null && !first.isSpawner()) {
                startMaterial = first.getMaterial();
            }
        }
        blockLoc.getBlock().setType(startMaterial);

        IslandData data = new IslandData(uuid, blockLoc, startMaterial);
        data.setBorderSize(borderDefaultSize);
        data.getSettings().putAll(defaultSettings);
        islands.put(uuid, data);
        return data;
    }

    /**
     * Supprime l'île de ce propriétaire. Si elle avait des coéquipiers, l'équipe est
     * dissoute au passage (ils redeviennent sans île, voir /ob start) : on ne peut pas
     * laisser l'index de membres pointer vers une île qui n'existe plus.
     */
    public void removeIsland(UUID uuid) {
        IslandData island = islands.remove(uuid);
        if (island != null) {
            for (UUID member : island.getMembers()) {
                memberIndex.remove(member);
                Player memberPlayer = Bukkit.getPlayer(member);
                if (memberPlayer != null) {
                    memberPlayer.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&cL'île que tu avais rejointe a été supprimée. Utilise &f/ob start &cpour créer la tienne."));
                }
            }
        }
        pendingInvites.entrySet().removeIf(e -> e.getValue().equals(uuid));
    }

    // ==================== GESTION D'ÉQUIPE ====================
    // Rejoindre une nouvelle équipe (/ob team accept) ne fusionne RIEN :
    // si le joueur possédait sa propre île, elle est intégralement supprimée
    // par removeIsland() avant qu'il ne rejoigne (build, niveau/blocs cassés,
    // bankis, tokens, Nether ET End), pour éviter tout doublement de
    // richesse en créant une île, en la remplissant, puis en rejoignant une équipe.

    /**
     * Envoie (ou remplace) une invitation d'équipe. Expire automatiquement après
     * INVITE_EXPIRY_TICKS si elle n'est ni acceptée, ni refusée.
     */
    public void invitePlayer(UUID ownerUuid, UUID targetUuid) {
        pendingInvites.put(targetUuid, ownerUuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ownerUuid.equals(pendingInvites.get(targetUuid))) {
                pendingInvites.remove(targetUuid);
            }
        }, INVITE_EXPIRY_TICKS);
    }

    /** Retourne l'uuid du propriétaire qui a invité ce joueur, ou null s'il n'y a pas d'invitation en attente. */
    public UUID getPendingInviteOwner(UUID targetUuid) {
        return pendingInvites.get(targetUuid);
    }

    public void clearPendingInvite(UUID targetUuid) {
        pendingInvites.remove(targetUuid);
    }

    /**
     * Ajoute un joueur comme coéquipier d'une île. L'appelant doit s'être assuré au
     * préalable que ce joueur n'a plus d'île propre à lui (voir /ob team accept).
     */
    public void addTeamMember(IslandData island, UUID memberUuid) {
        island.addMember(memberUuid);
        memberIndex.put(memberUuid, island.getOwner());
    }

    /** Retire un coéquipier d'une île (kick, ban ou départ volontaire). */
    public void removeTeamMember(IslandData island, UUID memberUuid) {
        island.removeMember(memberUuid);
        memberIndex.remove(memberUuid);
    }

    // ==================== CHAT D'ÉQUIPE (/ob chat) ====================

    // Joueurs ayant activé le mode "chat d'équipe" (tout leur chat classique part
    // uniquement vers leur île tant que le mode reste actif). Purement en mémoire :
    // pas besoin d'être persisté, il se réactive au choix à chaque connexion.
    private final Set<UUID> teamChatPlayers = new HashSet<>();

    public boolean isTeamChatEnabled(UUID uuid) {
        return teamChatPlayers.contains(uuid);
    }

    public void setTeamChatEnabled(UUID uuid, boolean enabled) {
        if (enabled) {
            teamChatPlayers.add(uuid);
        } else {
            teamChatPlayers.remove(uuid);
        }
    }

    /** Active/désactive le mode chat d'équipe pour ce joueur et retourne le nouvel état. */
    public boolean toggleTeamChat(UUID uuid) {
        boolean newState = !isTeamChatEnabled(uuid);
        setTeamChatEnabled(uuid, newState);
        return newState;
    }

    /**
     * Cherche l'île dont le monde OVERWORLD dédié est exactement celui donné
     * (comparaison d'objet World, pas de nom). Utilisé pour qu'un portail
     * pris sur l'île d'un joueur (visiteur, coéquipier ou même son
     * propriétaire) mène toujours au Nether/End DE CETTE île précise, et non
     * à celle du joueur qui a pris le portail.
     */
    public IslandData findIslandByOverworld(World world) {
        if (world == null) return null;
        for (IslandData island : islands.values()) {
            World islandWorld = island.getBlockLocation().getWorld();
            if (islandWorld != null && islandWorld.equals(world)) {
                return island;
            }
        }
        return null;
    }

    /**
     * Pendant symétrique de findIslandByOverworld : retrouve l'île dont le
     * monde Nether OU End dédié est celui donné, pour qu'un portail retour
     * pris dans le Nether/End d'une île ramène bien sur SON overworld, même
     * pour un visiteur qui n'est pas propriétaire de cette île.
     */
    public IslandData findIslandByNetherOrEnd(World world) {
        if (world == null) return null;
        for (IslandData island : islands.values()) {
            Location netherLoc = island.getNetherBlockLocation();
            if (netherLoc != null && world.equals(netherLoc.getWorld())) {
                return island;
            }
            Location endLoc = island.getEndBlockLocation();
            if (endLoc != null && world.equals(endLoc.getWorld())) {
                return island;
            }
        }
        return null;
    }

    /**
     * Cherche l'île dont le monde dédié correspond au pseudo donné, c'est-à-
     * dire dont le nom de monde est "{island-world-prefix}{pseudo}" (voir
     * getIslandWorldName). Utilisé par /istransfer pour retrouver l'île d'un
     * joueur à partir de son ANCIEN pseudo, même si son UUID Mojang actuel
     * ne correspond plus à ce pseudo (changement de pseudo).
     */
    public IslandData findIslandByWorldPseudo(String pseudo) {
        String expectedWorld = getIslandWorldName(new UUID(0, 0), pseudo);
        for (IslandData island : islands.values()) {
            World world = island.getBlockLocation().getWorld();
            if (world != null && world.getName().equalsIgnoreCase(expectedWorld)) {
                return island;
            }
        }
        return null;
    }

    /**
     * Transfère la propriété d'une île vers un nouveau joueur (admin only,
     * voir /istransfer). Le monde de l'île (son terrain, son bloc, ses
     * stats...) ne change pas : seul le propriétaire (UUID) associé change,
     * donc c'est bien "nouveauUuid" qui pourra désormais faire /ob tp,
     * /upgrades, etc. sur cette île.
     *
     * @return false si newUuid a déjà une île (transfert refusé pour éviter
     *         d'écraser une île existante).
     */
    public boolean transferIsland(IslandData island, UUID newUuid) {
        if (islands.containsKey(newUuid)) return false;
        UUID oldUuid = island.getOwner();
        islands.remove(oldUuid);
        island.setOwner(newUuid);
        islands.put(newUuid, island);
        return true;
    }

    /**
     * Pose sur le bloc OneBlock de l'île le résultat d'un tirage de loot pour
     * le niveau donné (bloc classique ou spawner), et met à jour les
     * métadonnées de l'île en conséquence. Utilisé à la fois pour la
     * régénération normale après une casse (BlockBreakListener) et pour la
     * remise à niveau 1 lors d'une renaissance (/ob renaissance).
     */
    public void regenerateBlock(IslandData island, OneBlockLevel level) {
        // IMPORTANT : island.setRegenerating(false) doit être appelé dans un
        // "finally" quoi qu'il arrive. Avant ce correctif, si rollLoot() ou
        // la pose du bloc levait une exception (loot mal configuré, chunk
        // pas encore chargé, cast CreatureSpawner qui échoue...), le drapeau
        // "regenerating" restait bloqué à true ET le bloc restait en AIR
        // pour toujours : le bloc OneBlock ne réapparaissait plus jamais.
        // Un joueur pouvait alors poser N'IMPORTE QUEL bloc à cet
        // emplacement ; comme BlockBreakListener ne vérifie l'emplacement
        // cassé QUE par ses coordonnées (et non le matériau réellement en
        // place), casser ce bloc posé à la main déclenchait quand même toute
        // la récompense + régénération du OneBlock -> le joueur pouvait
        // reposer/recasser indéfiniment et farmer des récompenses à l'infini
        // ("duplication"). Ce correctif garantit que le bloc est TOUJOURS
        // reposé et que le drapeau est TOUJOURS relâché.
        try {
            Block block = island.getBlockLocation().getBlock();

            // COFFRE BONUS : tirage indépendant de la loot-table du niveau.
            // S'il réussit, un coffre rempli de loot commun (jamais rare)
            // prend la place du bloc normalement tiré ce coup-ci, et on
            // s'arrête là (pas de bloc/spawner du niveau cette fois-ci).
            if (rollChestChance()) {
                block.setType(Material.CHEST, false);
                island.setCurrentMaterial(Material.CHEST);
                island.setCurrentSpawnerEntity(null);

                // CORRECTIF (coffres qui restaient toujours vides) : l'ancienne
                // version remplissait l'inventaire un tick plus tard, via
                // Bukkit.getScheduler().runTask(...), en supposant que le tile
                // entity du coffre n'était pas encore prêt dans le même tick.
                // En réalité le vrai problème est ailleurs : un joueur en mode
                // créatif (ou avec une casse instantanée) peut casser le bloc
                // qui vient d'apparaître DANS LE MÊME TICK que sa pose, avant
                // même que la tâche différée n'ait eu la moindre chance de
                // s'exécuter -> le coffre était cassé (et son contenu donné
                // via giveChestContents) alors qu'il était encore réellement
                // vide. On remplit donc maintenant l'inventaire de façon
                // strictement SYNCHRONE, avant même que ce tick ne se termine.
                // block.getState(false) (useSnapshot=false) donne en plus
                // l'état RÉEL et vivant du bloc (lié au vrai tile entity du
                // monde), et non une copie/snapshot déconnectée : les objets
                // ajoutés à cet inventaire sont donc immédiatement ceux du
                // vrai coffre, sans dépendre d'un quelconque délai.
                if (block.getState(false) instanceof org.bukkit.block.Chest chest) {
                    fillChestLoot(chest);
                } else {
                    plugin.getLogger().warning("[OneBlock] Impossible de remplir le coffre bonus pour l'île "
                            + island.getOwner() + " (bloc non reconnu comme coffre après la pose).");
                }
                return;
            }

            LootItem loot;
            try {
                loot = level != null ? level.rollLoot() : null;
            } catch (Exception ex) {
                plugin.getLogger().warning("[OneBlock] rollLoot() a échoué pour l'île "
                        + island.getOwner() + ", utilisation du bloc de secours. Erreur : " + ex);
                loot = null;
            }
            Material fallback = getFillerBlock();
            if (fallback == null) fallback = Material.STONE;

            Material resultMaterial;
            EntityType resultSpawner = null;

            if (loot == null) {
                resultMaterial = fallback;
            } else if (loot.isSpawner()) {
                resultMaterial = Material.SPAWNER;
                resultSpawner = loot.getEntity();
            } else {
                resultMaterial = loot.getMaterial();
            }

            block.setType(resultMaterial);

            // On vérifie que le bloc a réellement été posé (chunk chargé,
            // pas annulé par un plugin tiers...). Si ce n'est pas le cas, on
            // retente une fois de manière forcée avant d'abandonner, plutôt
            // que de laisser un trou définitif.
            if (block.getType() != resultMaterial) {
                block.setType(resultMaterial, false);
            }

            if (resultMaterial == Material.SPAWNER && resultSpawner != null
                    && block.getState() instanceof org.bukkit.block.CreatureSpawner spawner) {
                spawner.setSpawnedType(resultSpawner);
                spawner.update();
            }

            island.setCurrentMaterial(resultMaterial);
            island.setCurrentSpawnerEntity(resultMaterial == Material.SPAWNER ? resultSpawner : null);
        } catch (Exception ex) {
            plugin.getLogger().severe("[OneBlock] Erreur lors de la régénération du bloc pour l'île "
                    + island.getOwner() + " : " + ex);
        } finally {
            island.setRegenerating(false);
        }
    }

    /**
     * Supprime (AIR) tout le terrain déjà généré dans la zone où l'île va être
     * créée, pour que l'île commence dans un vrai vide (comme un OneBlock
     * classique) plutôt que mélangée avec les anciens blocs de la map.
     * Si clear-to-void est activé, le nettoyage descend jusqu'au fond du
     * monde (bedrock naturel compris) au lieu de s'arrêter à height-below
     * sous l'île, pour un vrai à-pic jusqu'au vide.
     */
    private void clearArea(World world, int centerX, int centerZ) {
        if (!clearEnabled || clearRadius <= 0) return;
        clearArea(world, centerX, centerZ, clearRadius);
    }

    /**
     * Variante bas niveau du nettoyage, utilisée par /ob reset : contrairement à
     * clearArea(world, x, z) ci-dessus, elle nettoie TOUJOURS (ignore
     * island-clear.enabled) et sur le rayon donné, pas forcément clearRadius.
     * En effet island-clear.enabled=false par défaut est pensé pour la
     * création d'île dans un monde tout neuf (déjà vide) : /ob reset, lui,
     * réutilise le même monde que l'ancienne île, donc les constructions du
     * joueur doivent explicitement être effacées, sans quoi seule la
     * progression OneBlock (niveau, bloc) repartirait à zéro.
     */
    private void clearArea(World world, int centerX, int centerZ, int radius) {
        if (radius <= 0) return;

        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;
        int minY = clearToVoid
                ? world.getMinHeight()
                : Math.max(world.getMinHeight(), islandY - clearBelow);
        int maxY = Math.min(world.getMaxHeight() - 1, islandY + clearAbove);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /**
     * Réinitialise complètement l'île de ce propriétaire pour /ob reset :
     * supprime les données (niveau, renaissances, points de renaissance,
     * améliorations /upgrades ET /rebirthshop, tokens, caisse d'île...) ET
     * efface physiquement tout le terrain généré par le joueur (constructions
     * comprises), sur un rayon couvrant au moins sa bordure actuelle (même
     * agrandie via /ob agrandir), avant de recréer une île toute neuve.
     * La nouvelle IslandData créée en fin de méthode remplace intégralement
     * l'ancienne : aucune donnée de progression n'est reportée dessus.
     */
    public IslandData resetIsland(UUID uuid, String playerName) {
        IslandData oldIsland = islands.get(uuid);
        World world = oldIsland != null ? oldIsland.getBlockLocation().getWorld() : null;
        int radius = clearRadius > 0 ? clearRadius : 25;
        if (oldIsland != null) {
            radius = Math.max(radius, oldIsland.getBorderSize() / 2 + 1);
        }

        removeIsland(uuid);

        if (world != null) {
            // Île posée en (0, islandY, 0) dans son propre monde dédié (voir createIsland).
            clearArea(world, 0, 0, radius);
        }

        return createIsland(uuid, playerName);
    }

    public Collection<IslandData> getAllIslands() {
        return islands.values();
    }

    // ==================== COFFRE PARTAGÉ DE LA CAISSE D'ÎLE (/bankis objets) ====================

    /**
     * Retourne l'Inventory vivant (partagé entre tous les joueurs de cette île)
     * du coffre de la caisse, en le créant à partir des données sauvegardées
     * s'il n'existe pas encore en mémoire.
     */
    public Inventory getOrCreateBankInventory(IslandData island) {
        return bankInventories.computeIfAbsent(island.getOwner(), k -> BankisItemsGUI.create(island));
    }

    /** Recopie le contenu vivant de chaque coffre ouvert dans l'IslandData correspondante, avant sauvegarde disque. */
    private void syncBankInventories() {
        for (Map.Entry<UUID, Inventory> entry : bankInventories.entrySet()) {
            IslandData island = islands.get(entry.getKey());
            if (island != null) {
                island.setBankItems(entry.getValue().getContents());
            }
        }
    }

    // ==================== PERSISTANCE (islands.yml) ====================

    public void loadIslands() {
        islandsFile = new File(plugin.getDataFolder(), "islands.yml");
        if (!islandsFile.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(islandsFile);
        // "next-slot" n'existe plus : chaque île a désormais son propre monde
        // (voir island-world-prefix), il n'y a plus d'espacement à suivre.

        ConfigurationSection root = yml.getConfigurationSection("islands");
        if (root == null) return;

        int migrated = 0;

        for (String uuidStr : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(uuidStr);
            if (sec == null) continue;

            UUID uuid = UUID.fromString(uuidStr);
            double x = sec.getDouble("x");
            double y = sec.getDouble("y");
            double z = sec.getDouble("z");
            Material mat = Material.matchMaterial(sec.getString("material", "STONE"));
            if (mat == null) mat = Material.STONE;

            String storedWorld = sec.getString("world", "world");
            String pseudo = Bukkit.getOfflinePlayer(uuid).getName();
            String expectedWorld = getIslandWorldName(uuid, pseudo);

            Location loc;
            if (storedWorld.equalsIgnoreCase(expectedWorld)) {
                // Déjà dans son propre monde dédié : rien à migrer.
                World world = getOrCreateIslandWorld(storedWorld);
                if (world == null) continue;
                loc = new Location(world, x, y, z);
            } else {
                // Île encore dans un ancien monde partagé (ex: "oneblock_world"
                // d'avant le passage à un monde dédié par île) -> migration
                // automatique et unique vers son propre monde "is_de_<pseudo>".
                // Bedrock + bloc actuel recréés là-bas, toutes les stats (banque,
                // niveau, upgrades...) sont conservées telles quelles.
                World newWorld = getOrCreateIslandWorld(expectedWorld);
                if (newWorld == null) continue;

                Location bedrockLoc = new Location(newWorld, 0, islandY, 0);
                Location blockLoc = new Location(newWorld, 0, islandY + 1, 0);
                bedrockLoc.getBlock().setType(Material.BEDROCK);
                blockLoc.getBlock().setType(mat);
                loc = blockLoc;

                plugin.getLogger().info("Île de " + (pseudo != null ? pseudo : uuidStr)
                        + " migrée de '" + storedWorld + "' vers son monde dédié '" + expectedWorld + "'.");
                migrated++;
            }

            IslandData data = new IslandData(uuid, loc, mat);
            data.setBrokenCount(sec.getInt("broken", 0));
            // Repli sur "broken" pour les îles sauvegardées avant l'ajout de ce
            // compteur à vie : au pire on repart de leur total actuel (jamais
            // moins), plutôt que de perdre leur historique en le remettant à 0.
            data.setTotalBrokenCount(sec.getInt("total-broken", data.getBrokenCount()));
            data.setBorderSize(sec.getInt("border-size", borderDefaultSize));
            data.setBankBalance(sec.getDouble("bank", 0));
            data.setRebirths(sec.getInt("rebirths", 0));
            data.setMobsKilled(sec.getInt("mobs-killed", 0));
            ConfigurationSection playerTokensSec = sec.getConfigurationSection("player-tokens");
            if (playerTokensSec != null) {
                for (String uuidStr2 : playerTokensSec.getKeys(false)) {
                    try {
                        data.setTokens(UUID.fromString(uuidStr2), playerTokensSec.getInt(uuidStr2, 0));
                    } catch (IllegalArgumentException ignored) {
                        // clé corrompue dans le fichier, on l'ignore simplement
                    }
                }
            } else if (sec.contains("tokens")) {
                // Migration depuis l'ancien format (un seul solde de tokens
                // partagé par île) : on l'attribue au propriétaire, faute de
                // mieux, la première fois que l'île est rechargée.
                data.setTokens(uuid, sec.getInt("tokens", 0));
            }
            data.setFlyUnlocked(sec.getBoolean("fly-unlocked", false));
            data.setHatUnlocked(sec.getBoolean("hat-unlocked", false));
            data.setFeedUnlocked(sec.getBoolean("feed-unlocked", false));
            data.setRebirthPoints(sec.getInt("rebirth-points", 0));
            data.setLocked(sec.getBoolean("locked", false));

            ItemStack[] bankItems = new ItemStack[54];
            ConfigurationSection bankItemsSec = sec.getConfigurationSection("bank-items");
            if (bankItemsSec != null) {
                for (String slotKey : bankItemsSec.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        if (slot >= 0 && slot < bankItems.length) {
                            bankItems[slot] = bankItemsSec.getItemStack(slotKey);
                        }
                    } catch (NumberFormatException ignored) {
                        // clé corrompue dans le fichier, on l'ignore simplement
                    }
                }
            }
            data.setBankItems(bankItems);

            ConfigurationSection upgradesSec = sec.getConfigurationSection("upgrades");
            if (upgradesSec != null) {
                for (String key : upgradesSec.getKeys(false)) {
                    data.setUpgradeLevel(key, upgradesSec.getInt(key, 0));
                }
            }
            ConfigurationSection rebirthShopSec = sec.getConfigurationSection("rebirth-shop");
            if (rebirthShopSec != null) {
                for (String key : rebirthShopSec.getKeys(false)) {
                    data.setRebirthShopLevel(key, rebirthShopSec.getInt(key, 0));
                }
            }

            ConfigurationSection settingsSec = sec.getConfigurationSection("settings");
            if (settingsSec != null) {
                for (String key : settingsSec.getKeys(false)) {
                    data.setSetting(key, settingsSec.getBoolean(key));
                }
            } else {
                data.getSettings().putAll(defaultSettings);
            }

            ConfigurationSection netherSettingsSec = sec.getConfigurationSection("nether-settings");
            if (netherSettingsSec != null) {
                for (String key : netherSettingsSec.getKeys(false)) {
                    data.getNetherSettings().put(key, netherSettingsSec.getBoolean(key));
                }
            }
            // NOTE : contrairement aux réglages overworld, les réglages Nether
            // ne sont volontairement PAS préremplis avec defaultSettings quand
            // absents : getSetting(World, ...) retombe déjà sur "def" (la
            // valeur par défaut demandée par l'appelant) si la clé est absente
            // de netherSettings, ce qui suffit et garde le fichier de
            // sauvegarde compact tant que le joueur n'a rien changé dans le
            // Nether.

            String entityStr = sec.getString("spawner-entity", null);
            if (entityStr != null) {
                try {
                    EntityType entityType = EntityType.valueOf(entityStr);
                    data.setCurrentSpawnerEntity(entityType);
                    // Si le bloc vient d'être recréé ailleurs (migration) et que
                    // c'est un spawner, il faut aussi configurer son type de mob.
                    if (mat == Material.SPAWNER
                            && data.getBlockLocation().getBlock().getState() instanceof org.bukkit.block.CreatureSpawner spawner) {
                        spawner.setSpawnedType(entityType);
                        spawner.update();
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            // ==================== NETHER / END (portail) ====================
            ConfigurationSection netherSec = sec.getConfigurationSection("nether");
            if (netherSec != null) {
                String nWorldName = netherSec.getString("world");
                World nWorld = nWorldName != null ? Bukkit.getWorld(nWorldName) : null;
                if (nWorld == null && nWorldName != null) {
                    nWorld = getOrCreateNetherWorld(nWorldName);
                }
                if (nWorld != null) {
                    Location nLoc = new Location(nWorld, netherSec.getDouble("x"), netherSec.getDouble("y"), netherSec.getDouble("z"));
                    data.setNetherBlockLocation(nLoc);
                    try {
                        data.setNetherCurrentMaterial(Material.valueOf(netherSec.getString("material", "NETHERRACK")));
                    } catch (IllegalArgumentException ignored) {
                        data.setNetherCurrentMaterial(Material.NETHERRACK);
                    }
                    data.setNetherBrokenCount(netherSec.getInt("broken", 0));
                    for (String matName : netherSec.getStringList("mined-types")) {
                        try {
                            data.recordNetherMined(Material.valueOf(matName));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
            ConfigurationSection endSec = sec.getConfigurationSection("end");
            if (endSec != null) {
                String eWorldName = endSec.getString("world");
                World eWorld = eWorldName != null ? Bukkit.getWorld(eWorldName) : null;
                if (eWorld == null && eWorldName != null) {
                    eWorld = getOrCreateEndWorld(eWorldName);
                }
                if (eWorld != null) {
                    Location eLoc = new Location(eWorld, endSec.getDouble("x"), endSec.getDouble("y"), endSec.getDouble("z"));
                    data.setEndBlockLocation(eLoc);
                    try {
                        data.setEndCurrentMaterial(Material.valueOf(endSec.getString("material", "END_STONE")));
                    } catch (IllegalArgumentException ignored) {
                        data.setEndCurrentMaterial(Material.END_STONE);
                    }
                    data.setEndBrokenCount(endSec.getInt("broken", 0));
                    for (String matName : endSec.getStringList("mined-types")) {
                        try {
                            data.recordEndMined(Material.valueOf(matName));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }

            islands.put(uuid, data);
        }

        // Équipe (membres, permissions, bannis) : chargée dans une seconde passe une fois
        // TOUTES les îles en mémoire, pour reconstruire l'index inverse memberIndex.
        memberIndex.clear();
        for (String uuidStr : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(uuidStr);
            if (sec == null) continue;
            UUID ownerUuid = UUID.fromString(uuidStr);
            IslandData data = islands.get(ownerUuid);
            if (data == null) continue;

            ConfigurationSection teamSec = sec.getConfigurationSection("team");
            if (teamSec != null) {
                ConfigurationSection membersSec = teamSec.getConfigurationSection("members");
                if (membersSec != null) {
                    for (String memberStr : membersSec.getKeys(false)) {
                        try {
                            UUID memberUuid = UUID.fromString(memberStr);
                            data.addMember(memberUuid);
                            memberIndex.put(memberUuid, ownerUuid);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                for (String bannedStr : teamSec.getStringList("banned")) {
                    try {
                        data.banPlayer(UUID.fromString(bannedStr));
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                // Joueurs "de confiance" (/ob trust) : contrairement aux membres
                // ci-dessus, ils ne rejoignent PAS memberIndex, puisqu'ils
                // gardent leur propre île (getIsland(uuid) doit continuer à
                // retourner LEUR île, pas celle-ci).
                ConfigurationSection trustedSec = teamSec.getConfigurationSection("trusted");
                if (trustedSec != null) {
                    for (String trustedStr : trustedSec.getKeys(false)) {
                        try {
                            data.addTrusted(UUID.fromString(trustedStr));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }

                // Permissions par RÔLE (/ob perms) : une table par rôle (VISITEUR/TRUST/MEMBRE),
                // commune à tous les joueurs de ce rôle sur cette île.
                ConfigurationSection rolesSec = teamSec.getConfigurationSection("role-permissions");
                if (rolesSec != null) {
                    for (String roleStr : rolesSec.getKeys(false)) {
                        try {
                            fr.tuto.oneblock.models.Role role = fr.tuto.oneblock.models.Role.valueOf(roleStr);
                            ConfigurationSection permsSec = rolesSec.getConfigurationSection(roleStr);
                            if (permsSec == null) continue;
                            for (String permKey : permsSec.getKeys(false)) {
                                fr.tuto.oneblock.models.IslandPermission perm =
                                        fr.tuto.oneblock.models.IslandPermission.byKey(permKey);
                                boolean def = perm == null || perm.defaultFor(role);
                                data.setRolePermission(role, permKey, permsSec.getBoolean(permKey, def));
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
        }

        plugin.getLogger().info(islands.size() + " îles OneBlock chargées depuis islands.yml.");
        if (migrated > 0) {
            plugin.getLogger().info(migrated + " île(s) migrée(s) vers leur monde dédié (is_de_<pseudo>).");
            saveIslands();
        }
    }

    public void saveIslands() {
        if (islandsFile == null) {
            islandsFile = new File(plugin.getDataFolder(), "islands.yml");
        }
        syncBankInventories();
        YamlConfiguration yml = new YamlConfiguration();

        for (Map.Entry<UUID, IslandData> entry : islands.entrySet()) {
            String path = "islands." + entry.getKey();
            IslandData d = entry.getValue();
            Location loc = d.getBlockLocation();
            yml.set(path + ".world", loc.getWorld().getName());
            yml.set(path + ".x", loc.getX());
            yml.set(path + ".y", loc.getY());
            yml.set(path + ".z", loc.getZ());
            yml.set(path + ".material", d.getCurrentMaterial().name());
            yml.set(path + ".broken", d.getBrokenCount());
            yml.set(path + ".total-broken", d.getTotalBrokenCount());
            yml.set(path + ".border-size", d.getBorderSize());
            yml.set(path + ".bank", d.getBankBalance());
            yml.set(path + ".rebirths", d.getRebirths());
            yml.set(path + ".mobs-killed", d.getMobsKilled());
            for (Map.Entry<UUID, Integer> tok : d.getPlayerTokens().entrySet()) {
                yml.set(path + ".player-tokens." + tok.getKey(), tok.getValue());
            }
            yml.set(path + ".fly-unlocked", d.isFlyUnlocked());
            yml.set(path + ".hat-unlocked", d.isHatUnlocked());
            yml.set(path + ".feed-unlocked", d.isFeedUnlocked());
            yml.set(path + ".rebirth-points", d.getRebirthPoints());
            yml.set(path + ".locked", d.isLocked());

            if (d.hasNetherIsland()) {
                Location nLoc = d.getNetherBlockLocation();
                yml.set(path + ".nether.world", nLoc.getWorld().getName());
                yml.set(path + ".nether.x", nLoc.getX());
                yml.set(path + ".nether.y", nLoc.getY());
                yml.set(path + ".nether.z", nLoc.getZ());
                yml.set(path + ".nether.material", d.getNetherCurrentMaterial().name());
                yml.set(path + ".nether.broken", d.getNetherBrokenCount());
                yml.set(path + ".nether.mined-types", d.getNetherMinedTypes().stream().map(Enum::name).toList());
            }
            if (d.hasEndIsland()) {
                Location eLoc = d.getEndBlockLocation();
                yml.set(path + ".end.world", eLoc.getWorld().getName());
                yml.set(path + ".end.x", eLoc.getX());
                yml.set(path + ".end.y", eLoc.getY());
                yml.set(path + ".end.z", eLoc.getZ());
                yml.set(path + ".end.material", d.getEndCurrentMaterial().name());
                yml.set(path + ".end.broken", d.getEndBrokenCount());
                yml.set(path + ".end.mined-types", d.getEndMinedTypes().stream().map(Enum::name).toList());
            }
            ItemStack[] bankItems = d.getBankItems();
            if (bankItems != null) {
                for (int i = 0; i < bankItems.length; i++) {
                    if (bankItems[i] != null) {
                        yml.set(path + ".bank-items." + i, bankItems[i]);
                    }
                }
            }
            for (Map.Entry<String, Integer> upg : d.getUpgradeLevels().entrySet()) {
                yml.set(path + ".upgrades." + upg.getKey(), upg.getValue());
            }
            for (Map.Entry<String, Integer> rsUpg : d.getRebirthShopLevels().entrySet()) {
                yml.set(path + ".rebirth-shop." + rsUpg.getKey(), rsUpg.getValue());
            }
            for (Map.Entry<String, Boolean> setting : d.getSettings().entrySet()) {
                yml.set(path + ".settings." + setting.getKey(), setting.getValue());
            }
            for (Map.Entry<String, Boolean> netherSetting : d.getNetherSettings().entrySet()) {
                yml.set(path + ".nether-settings." + netherSetting.getKey(), netherSetting.getValue());
            }
            if (d.getCurrentSpawnerEntity() != null) {
                yml.set(path + ".spawner-entity", d.getCurrentSpawnerEntity().name());
            }
            for (UUID member : d.getMembers()) {
                // Case vide (juste pour marquer sa présence) : ses permissions
                // sont désormais celles du rôle MEMBRE, sauvegardées séparément.
                yml.set(path + ".team.members." + member, true);
            }
            if (!d.getBannedPlayers().isEmpty()) {
                yml.set(path + ".team.banned", d.getBannedPlayers().stream().map(UUID::toString).toList());
            }
            for (UUID trusted : d.getTrustedPlayers()) {
                yml.set(path + ".team.trusted." + trusted, true);
            }
            for (fr.tuto.oneblock.models.Role role : fr.tuto.oneblock.models.Role.values()) {
                for (Map.Entry<String, Boolean> perm : d.getRolePermissions(role).entrySet()) {
                    yml.set(path + ".team.role-permissions." + role + "." + perm.getKey(), perm.getValue());
                }
            }
        }

        try {
            yml.save(islandsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder islands.yml: " + e.getMessage());
        }
    }
}
