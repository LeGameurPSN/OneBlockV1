package fr.tuto.oneblock.gui;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import fr.tuto.oneblock.models.ShopUpgrade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI de la boutique de renaissance (/rebirthshop) : améliorations
 * permanentes et puissantes, achetables avec les points de renaissance
 * gagnés à chaque utilisation de /rebirth (voir config.yml, section
 * "rebirth-shop"). Système identique à /upgrades mais avec une monnaie
 * différente (points au lieu d'argent).
 */
public class RebirthShopGUI {

    public static final String TITLE = "Boutique de Renaissance";
    private static final int SIZE = 27;
    private static final int BALANCE_SLOT = 4;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    /** Associe chaque clé d'amélioration configurée à son slot dans le GUI. */
    public static Map<String, Integer> getSlots(OneBlockPlugin plugin) {
        Map<String, Integer> slots = new LinkedHashMap<>();
        int i = 0;
        for (String key : plugin.getManager().getRebirthShopItems().keySet()) {
            if (i >= CONTENT_SLOTS.length) break;
            slots.put(key, CONTENT_SLOTS[i++]);
        }
        return slots;
    }

    public static void open(OneBlockPlugin plugin, Player player, IslandData island) {
        RebirthShopGuiHolder holder = new RebirthShopGuiHolder(player);
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8" + TITLE));
        holder.setInventory(inv);

        fill(plugin, inv, island);
        player.openInventory(inv);
    }

    public static void refresh(OneBlockPlugin plugin, Inventory inv, IslandData island) {
        fill(plugin, inv, island);
    }

    private static void fill(OneBlockPlugin plugin, Inventory inv, IslandData island) {
        inv.clear();

        ItemStack border = namedItemCmd(Material.PURPLE_STAINED_GLASS_PANE, 533003, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(BALANCE_SLOT, buildBalanceItem(island));

        OneBlockManager manager = plugin.getManager();
        Map<String, Integer> slots = getSlots(plugin);
        for (ShopUpgrade def : manager.getRebirthShopItems().values()) {
            Integer slot = slots.get(def.key());
            if (slot == null) continue;
            int level = island.getRebirthShopLevel(def.key());
            int cost = manager.getRebirthShopCost(island, def);
            inv.setItem(slot, buildUpgradeItem(def, level, cost));
        }
    }

    private static ItemStack buildBalanceItem(IslandData island) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&d&lTon solde: &f" + island.getRebirthPoints() + " points"));
        meta.lore(List.of(
                LEGACY.deserialize("&7Gagnés à chaque renaissance &f(/rebirth)&7."),
                LEGACY.deserialize("&7Dépensables ici sur des bonus permanents.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildUpgradeItem(ShopUpgrade def, int level, int cost) {
        ItemStack item = new ItemStack(def.icon());
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>();
        for (String line : def.description()) {
            if (!line.isBlank()) lore.add(LEGACY.deserialize("&7" + line));
        }
        lore.add(LEGACY.deserialize("&8"));

        String name;
        if (def.repeatable()) {
            // Article répétable (ex: "5000 Argent") : pas de palier, prix
            // fixe, achetable autant de fois que voulu.
            name = "&d✦ " + def.name();
            if (level > 0) {
                lore.add(LEGACY.deserialize("&7Déjà acheté: &e" + level + " fois"));
            }
            lore.add(LEGACY.deserialize("&7Prix: &d" + cost + " points &7(à chaque achat)"));
            lore.add(LEGACY.deserialize("&e▶ Clique pour acheter"));
            meta.displayName(LEGACY.deserialize(name));
            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        boolean maxed = level >= def.maxLevel();
        lore.add(LEGACY.deserialize("&7Niveau: &e" + level + "&7/&e" + def.maxLevel()));
        if (level > 0) {
            lore.add(LEGACY.deserialize("&7Bonus actuel: &b+" + trim(def.bonusAtLevel(level)) + "%"));
        }

        if (maxed) {
            name = "&a✔ " + def.name() + " &7(MAX)";
        } else {
            name = "&d✦ " + def.name();
            lore.add(LEGACY.deserialize("&7Prix du niveau " + (level + 1) + ": &d" + cost + " points"));
            lore.add(LEGACY.deserialize("&e▶ Clique pour améliorer"));
        }
        meta.displayName(LEGACY.deserialize(name));
        meta.lore(lore);

        if (level > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static String trim(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
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
