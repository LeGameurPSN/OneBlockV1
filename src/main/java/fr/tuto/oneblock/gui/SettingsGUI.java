package fr.tuto.oneblock.gui;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsGUI {

    public static final String TITLE = "Paramètres - OneBlock";
    private static final int SIZE = 54;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // Ordre d'affichage des réglages dans le GUI.
    // NOTE: "keep-inventory" est volontairement ABSENT de cette liste : ce
    // réglage n'est pas modifiable par les joueurs (il est fixé globalement
    // par l'admin dans config.yml -> default-settings.keep-inventory).
    // NOTE: "animal-spawning" est également ABSENT : le spawn des mobs
    // passifs liés au biome de l'île (vache, mouton, poulet, etc.) doit
    // toujours être actif et n'est pas désactivable par les joueurs (voir
    // IslandSettingsListener#onCreatureSpawn, qui ne vérifie plus ce
    // réglage). Seuls les mobs hostiles ("mob-spawning") restent
    // désactivables.
    // "leaf-decay" n'est pas un réglage par île : la décomposition des
    // feuilles suit désormais le comportement vanilla normal partout (sauf
    // sur le bloc OneBlock lui-même, protégé en dur, voir
    // IslandSettingsListener#onLeavesDecay). Ce n'est donc pas un choix
    // affiché dans ce menu.
    // feuilles est désormais désactivée en dur pour tout le monde (voir
    // IslandSettingsListener#onLeavesDecay), ce n'est plus un choix par île.
    // "item-pickup" a également été retiré de cette liste : le ramassage
    // des objets est désormais géré UNIQUEMENT par la permission de rôle
    // "item_pickup" (visiteur/trust/membre, voir IslandPermission et
    // /ob permissions), pas par un interrupteur global d'île qui
    // s'appliquait à tout le monde y compris trust/membre.
    private static final String[] KEYS = {
            "mob-spawning", "pvp", "monster-damage", "fire-spread",
            "fire-damage", "explosions", "mob-griefing", "fall-damage", "drown-damage",
            "void-damage", "suffocation-damage", "freeze-damage", "cactus-damage", "lightning-damage",
            "hunger-loss", "natural-regeneration", "crop-trample",
            "mob-drops", "xp-drop", "potion-splash", "villager-trading", "bucket-use",
            "build", "block-break-other", "bow-shooting", "entity-mount", "always-day",
            "fly", "border-particles"
    };

    // Grille intérieure de 9 colonnes x 4 lignes (36 emplacements) au centre
    // d'un coffre double. Largement assez pour les 32 réglages actuels tout
    // en laissant de la marge pour en ajouter d'autres plus tard.
    private static final int[] CONTENT_SLOTS;
    static {
        CONTENT_SLOTS = new int[36];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 0; col <= 8; col++) {
                CONTENT_SLOTS[idx++] = row * 9 + col;
            }
        }
    }

    private static final Map<String, Integer> SLOTS = new LinkedHashMap<>();
    private static final Map<String, Material> ICONS = new LinkedHashMap<>();

    static {
        for (int i = 0; i < KEYS.length; i++) {
            SLOTS.put(KEYS[i], CONTENT_SLOTS[i]);
        }

        ICONS.put("mob-spawning", Material.ZOMBIE_HEAD);
        ICONS.put("pvp", Material.IRON_SWORD);
        ICONS.put("monster-damage", Material.SKELETON_SKULL);
        ICONS.put("fire-spread", Material.FLINT_AND_STEEL);
        ICONS.put("fire-damage", Material.BLAZE_POWDER);
        ICONS.put("explosions", Material.TNT);
        ICONS.put("mob-griefing", Material.ENDER_PEARL);
        ICONS.put("fall-damage", Material.FEATHER);
        ICONS.put("drown-damage", Material.TRIDENT);
        ICONS.put("void-damage", Material.ENDER_EYE);
        ICONS.put("suffocation-damage", Material.SAND);
        ICONS.put("freeze-damage", Material.POWDER_SNOW_BUCKET);
        ICONS.put("cactus-damage", Material.CACTUS);
        ICONS.put("lightning-damage", Material.LIGHTNING_ROD);
        ICONS.put("hunger-loss", Material.COOKED_BEEF);
        ICONS.put("natural-regeneration", Material.GOLDEN_APPLE);
        ICONS.put("crop-trample", Material.WHEAT);
        ICONS.put("mob-drops", Material.BONE);
        ICONS.put("xp-drop", Material.EXPERIENCE_BOTTLE);
        ICONS.put("potion-splash", Material.SPLASH_POTION);
        ICONS.put("villager-trading", Material.EMERALD);
        ICONS.put("bucket-use", Material.BUCKET);
        ICONS.put("build", Material.BRICKS);
        ICONS.put("block-break-other", Material.IRON_PICKAXE);
        ICONS.put("bow-shooting", Material.BOW);
        ICONS.put("entity-mount", Material.SADDLE);
        ICONS.put("always-day", Material.CLOCK);
        ICONS.put("fly", Material.ELYTRA);
        ICONS.put("border-particles", Material.REDSTONE);
    }

    public static Map<String, Integer> getSlots() {
        return SLOTS;
    }

    public static void open(OneBlockPlugin plugin, Player player, IslandData island) {
        OneBlockGuiHolder holder = new OneBlockGuiHolder(player);
        boolean inNether = island.isNetherWorld(player.getWorld());
        String title = inNether ? TITLE + " (Nether)" : TITLE;
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8" + title));
        holder.setInventory(inv);

        fill(plugin, inv, island, player.getWorld());
        player.openInventory(inv);
    }

    public static void refresh(OneBlockPlugin plugin, Inventory inv, IslandData island, org.bukkit.World world) {
        fill(plugin, inv, island, world);
    }

    private static void fill(OneBlockPlugin plugin, Inventory inv, IslandData island, org.bukkit.World world) {
        inv.clear();

        ItemStack border = namedItemCmd(Material.GRAY_STAINED_GLASS_PANE, 533005, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        var namesSection = plugin.getConfig().getConfigurationSection("settings-names");

        for (Map.Entry<String, Integer> entry : SLOTS.entrySet()) {
            String key = entry.getKey();
            int slot = entry.getValue();
            // Les réglages Nether sont indépendants de ceux de l'overworld
            // (voir IslandData#getSetting(World, ...)) : on lit dans la bonne
            // table selon le monde actuel du joueur.
            boolean value = island.getSetting(world, key, plugin.getManager().getDefaultSettings().getOrDefault(key, true));
            String label = namesSection != null ? namesSection.getString(key, key) : key;
            Material icon = ICONS.getOrDefault(key, Material.PAPER);

            if (key.equals("fly") && !island.isFlyUnlocked()) {
                inv.setItem(slot, buildLockedFlyItem(label, plugin.getManager().getTokenFlyCost()));
            } else {
                inv.setItem(slot, buildToggleItem(icon, label, value));
            }
        }
    }

    private static ItemStack buildLockedFlyItem(String label, int cost) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(LEGACY.deserialize("&8🔒 " + label));
        meta.lore(java.util.List.of(
                LEGACY.deserialize("&7État: &8Verrouillé"),
                LEGACY.deserialize("&8"),
                LEGACY.deserialize("&7À débloquer avec &f/token buy fly"),
                LEGACY.deserialize("&7Coût: &b" + cost + " tokens")
        ));

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildToggleItem(Material icon, String label, boolean value) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();

        String status = value ? "&a✔ " + label : "&c✘ " + label;
        meta.displayName(LEGACY.deserialize(status));

        meta.lore(java.util.List.of(
                LEGACY.deserialize(value ? "&7État: &aActivé" : "&7État: &cDésactivé"),
                LEGACY.deserialize("&8"),
                LEGACY.deserialize(value ? "&e▶ Clique pour désactiver" : "&e▶ Clique pour activer")
        ));

        if (value) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack namedItemCmd(Material material, int customModelData, String name) {
        ItemStack item = namedItem(material, name);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(customModelData);
        item.setItemMeta(meta);
        return item;
    }
}
