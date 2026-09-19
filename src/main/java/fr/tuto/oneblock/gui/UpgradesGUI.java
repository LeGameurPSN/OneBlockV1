package fr.tuto.oneblock.gui;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.managers.VaultManager;
import fr.tuto.oneblock.models.IslandData;
import fr.tuto.oneblock.models.ShopUpgrade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
 * GUI de la boutique d'améliorations d'île (/upgrades) : liste, avec leur
 * prix en argent (Vault), les améliorations achetables définies dans
 * config.yml (section "upgrades"). Chaque amélioration a plusieurs niveaux,
 * le coût augmentant à chaque achat (voir OneBlockManager#getUpgradeCost).
 */
public class UpgradesGUI {

    public static final String TITLE = "Améliorations d'île";
    private static final int SIZE = 27;
    private static final int BALANCE_SLOT = 4;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // Slots centraux de la rangée du milieu, un par amélioration (jusqu'à 7,
    // dans l'ordre où elles apparaissent dans config.yml).
    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    /** Associe chaque clé d'amélioration configurée à son slot dans le GUI. */
    public static Map<String, Integer> getSlots(OneBlockPlugin plugin) {
        Map<String, Integer> slots = new LinkedHashMap<>();
        int i = 0;
        for (String key : plugin.getManager().getUpgrades().keySet()) {
            if (i >= CONTENT_SLOTS.length) break;
            slots.put(key, CONTENT_SLOTS[i++]);
        }
        return slots;
    }

    public static void open(OneBlockPlugin plugin, Player player, IslandData island) {
        UpgradesGuiHolder holder = new UpgradesGuiHolder(player);
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

        ItemStack border = namedItemCmd(Material.CYAN_STAINED_GLASS_PANE, 533008, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(BALANCE_SLOT, buildBalanceItem(plugin, island));

        OneBlockManager manager = plugin.getManager();
        Map<String, Integer> slots = getSlots(plugin);
        for (ShopUpgrade def : manager.getUpgrades().values()) {
            Integer slot = slots.get(def.key());
            if (slot == null) continue;
            int level = island.getUpgradeLevel(def.key());
            double cost = manager.getUpgradeCost(island, def);
            inv.setItem(slot, buildUpgradeItem(plugin, def, level, cost));
        }
    }

    private static ItemStack buildBalanceItem(OneBlockPlugin plugin, IslandData island) {
        VaultManager vault = plugin.getVaultManager();
        OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwner());
        String balance = vault.isEnabled() ? vault.format(vault.getBalance(owner)) : "?";

        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&a&lTon solde: &f" + balance));
        meta.lore(List.of(
                LEGACY.deserialize("&7Achète des améliorations permanentes"),
                LEGACY.deserialize("&7pour ton île avec ton argent.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildUpgradeItem(OneBlockPlugin plugin, ShopUpgrade def, int level, double cost) {
        boolean maxed = level >= def.maxLevel();
        ItemStack item = new ItemStack(def.icon());
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>();
        for (String line : def.description()) {
            if (!line.isBlank()) lore.add(LEGACY.deserialize("&7" + line));
        }
        lore.add(LEGACY.deserialize("&8"));
        lore.add(LEGACY.deserialize("&7Niveau: &e" + level + "&7/&e" + def.maxLevel()));
        if (level > 0) {
            lore.add(LEGACY.deserialize("&7Bonus actuel: &b+" + trim(def.bonusAtLevel(level)) + "%"));
        }

        String name;
        VaultManager vault = plugin.getVaultManager();
        if (maxed) {
            name = "&a✔ " + def.name() + " &7(MAX)";
        } else {
            name = "&d✦ " + def.name();
            lore.add(LEGACY.deserialize("&7Prix du niveau " + (level + 1) + ": &e" + vault.format(cost)));
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
