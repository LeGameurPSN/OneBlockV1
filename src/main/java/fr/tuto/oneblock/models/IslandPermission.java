package fr.tuto.oneblock.models;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Les permissions précises gérables via /ob perms (/ob permissions), une
 * par ligne du GUI (voir RolePermissionsGUI : 5 lignes de 9 = 45 cases,
 * 38 utilisées).
 * Chaque permission a une valeur par défaut différente selon le rôle
 * (VISITEUR / TRUST / MEMBRE) ; le propriétaire n'est jamais concerné, il a
 * toujours accès à tout (voir IslandData#hasPermission).
 */
public enum IslandPermission {

    // ---- Base ----
    BUILD("build", "Construire", Material.BRICKS, false, true, true),
    BREAK("break", "Casser des blocs", Material.IRON_PICKAXE, false, true, true),
    CONTAINERS("containers", "Coffres / conteneurs", Material.CHEST, false, true, true),
    BANK("bank", "Banque (argent + objets)", Material.GOLD_INGOT, false, false, true),
    PVP("pvp", "Combattre les joueurs", Material.IRON_SWORD, true, true, true),

    // ---- Blocs interactifs ----
    DOORS("doors", "Portes", Material.OAK_DOOR, false, true, true),
    TRAPDOORS_GATES("trapdoors_gates", "Trappes / portails", Material.OAK_TRAPDOOR, false, true, true),
    BUTTONS("buttons", "Boutons", Material.OAK_BUTTON, false, true, true),
    LEVERS("levers", "Leviers", Material.LEVER, false, true, true),
    PRESSURE_PLATES("pressure_plates", "Plaques de pression", Material.OAK_PRESSURE_PLATE, false, true, true),
    REDSTONE_COMPONENTS("redstone_components", "Redstone (répéteurs...)", Material.REPEATER, false, true, true),
    NOTE_JUKEBOX("note_jukebox", "Bloc de note / Jukebox", Material.JUKEBOX, false, true, true),
    BEDS("beds", "Lits", Material.RED_BED, false, true, true),
    BELL("bell", "Cloche", Material.BELL, false, true, true),
    LECTERN("lectern", "Lutrin", Material.LECTERN, false, true, true),
    CAKE_COMPOSTER("cake_composter", "Gâteau / Composteur", Material.CAKE, false, true, true),
    FLOWER_POTS("flower_pots", "Pots de fleurs", Material.FLOWER_POT, false, true, true),

    // ---- Ateliers ----
    CRAFTING_TABLE("crafting_table", "Table de craft", Material.CRAFTING_TABLE, true, true, true),
    ANVIL("anvil", "Enclume", Material.ANVIL, true, true, true),
    ENCHANTING_TABLE("enchanting_table", "Table d'enchantement", Material.ENCHANTING_TABLE, true, true, true),
    BREWING_STAND("brewing_stand", "Alambic", Material.BREWING_STAND, true, true, true),
    GRINDSTONE("grindstone", "Meule", Material.GRINDSTONE, true, true, true),
    LOOM("loom", "Métier à tisser", Material.LOOM, true, true, true),
    STONECUTTER("stonecutter", "Tailleur de pierre", Material.STONECUTTER, true, true, true),
    CARTOGRAPHY_TABLE("cartography_table", "Table de cartographie", Material.CARTOGRAPHY_TABLE, true, true, true),
    SMITHING_TABLE("smithing_table", "Table de forge", Material.SMITHING_TABLE, true, true, true),

    // ---- Conteneurs spéciaux ----
    ENDER_CHEST("ender_chest", "Coffre enderien", Material.ENDER_CHEST, true, true, true),
    HOPPERS("hoppers", "Entonnoirs", Material.HOPPER, false, true, true),

    // ---- Entités ----
    ITEM_FRAMES("item_frames", "Cadres / tableaux", Material.ITEM_FRAME, false, true, true),
    ARMOR_STANDS("armor_stands", "Porte-armures", Material.ARMOR_STAND, false, true, true),
    VILLAGER_TRADE("villager_trade", "Commerce villageois", Material.EMERALD, true, true, true),
    ANIMALS_SHEAR("animals_shear", "Tondre les moutons", Material.SHEARS, false, true, true),
    VEHICLES("vehicles", "Monter minecart / barque", Material.MINECART, true, true, true),
    LEASH("leash", "Attacher en laisse", Material.LEAD, false, true, true),
    ITEM_PICKUP("item_pickup", "Ramasser les objets", Material.HOPPER_MINECART, false, true, true),
    MOB_ATTACK("mob_attack", "Frapper les mobs/animaux", Material.BONE, false, true, true),
    ITEM_DROP("item_drop", "Jeter des objets au sol", Material.DROPPER, false, true, true),

    // ---- Autres ----
    FISHING("fishing", "Pêcher", Material.FISHING_ROD, true, true, true);

    private final String key;
    private final String label;
    private final Material icon;
    private final boolean defaultVisiteur;
    private final boolean defaultTrust;
    private final boolean defaultMembre;

    IslandPermission(String key, String label, Material icon,
                      boolean defaultVisiteur, boolean defaultTrust, boolean defaultMembre) {
        this.key = key;
        this.label = label;
        this.icon = icon;
        this.defaultVisiteur = defaultVisiteur;
        this.defaultTrust = defaultTrust;
        this.defaultMembre = defaultMembre;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public Material getIcon() {
        return icon;
    }

    public boolean defaultFor(Role role) {
        return switch (role) {
            case VISITEUR -> defaultVisiteur;
            case TRUST -> defaultTrust;
            case MEMBRE -> defaultMembre;
        };
    }

    private static final Map<String, IslandPermission> BY_KEY = new LinkedHashMap<>();
    static {
        for (IslandPermission perm : values()) {
            BY_KEY.put(perm.key, perm);
        }
    }

    public static IslandPermission byKey(String key) {
        return BY_KEY.get(key);
    }

    /** Construit la table de permissions par défaut d'un rôle donné (clé -> autorisé), dans l'ordre du GUI. */
    public static Map<String, Boolean> defaultsFor(Role role) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (IslandPermission perm : values()) {
            map.put(perm.key, perm.defaultFor(role));
        }
        return map;
    }
}
