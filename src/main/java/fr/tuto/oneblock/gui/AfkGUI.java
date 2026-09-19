package fr.tuto.oneblock.gui;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.AfkZoneManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI des récompenses de la zone AFK (/afk) : affiche les tokens, l'argent
 * et les clés actuellement en attente (accumulés en restant dans la zone
 * AFK), avec un bouton centré en bas pour tout récupérer d'un coup.
 */
public class AfkGUI {

    public static final String TITLE = "Zone AFK - Récompenses";
    private static final int SIZE = 27;

    private static final int TOKEN_SLOT = 11;
    private static final int MONEY_SLOT = 12;
    // Un slot par type de clé configuré, dans l'ordre de config.yml (jusqu'à 4).
    private static final int[] KEY_SLOTS = {13, 14, 15, 16};
    // Centre de la première rangée (haut du GUI) -> horloge/compte à rebours.
    public static final int CLOCK_SLOT = 4;
    // Centre de la dernière rangée d'un inventaire 27 slots (3x9) -> bouton "en bas centré".
    public static final int CLAIM_SLOT = 22;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static void open(OneBlockPlugin plugin, Player player) {
        AfkGuiHolder holder = new AfkGuiHolder(player);
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8" + TITLE));
        holder.setInventory(inv);

        fill(plugin, inv, player);
        player.openInventory(inv);
    }

    public static void refresh(OneBlockPlugin plugin, Inventory inv, Player player) {
        fill(plugin, inv, player);
    }

    private static void fill(OneBlockPlugin plugin, Inventory inv, Player player) {
        inv.clear();

        ItemStack border = namedItemCmd(Material.CYAN_STAINED_GLASS_PANE, 533001, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        AfkZoneManager afkManager = plugin.getAfkZoneManager();
        AfkZoneManager.PendingRewards pending = afkManager.getPending(player.getUniqueId());

        inv.setItem(CLOCK_SLOT, buildClockItem(afkManager.getSecondsUntilNextReward(player.getUniqueId())));
        inv.setItem(TOKEN_SLOT, buildTokenItem(pending));
        inv.setItem(MONEY_SLOT, buildMoneyItem(plugin, pending));

        List<AfkZoneManager.AfkKeyDef> keyDefs = afkManager.getKeyDefinitions();
        for (int i = 0; i < keyDefs.size() && i < KEY_SLOTS.length; i++) {
            AfkZoneManager.AfkKeyDef def = keyDefs.get(i);
            int count = pending.getKeyCount(def.id());
            inv.setItem(KEY_SLOTS[i], buildKeyItem(def, count));
        }

        inv.setItem(CLAIM_SLOT, buildClaimItem(!pending.isEmpty()));
    }

    /** Met à jour uniquement l'item horloge (appelé chaque seconde tant que le GUI est ouvert). */
    public static void updateCountdown(Inventory inv, long secondsRemaining) {
        inv.setItem(CLOCK_SLOT, buildClockItem(secondsRemaining));
    }

    private static ItemStack buildClockItem(long secondsRemaining) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (secondsRemaining < 0) {
            // -1 : le joueur n'est pas actuellement dans la zone AFK, le
            // compte à rebours ne défile pas pour lui.
            meta.displayName(LEGACY.deserialize("&7⏰ &fPas dans la zone AFK"));
            meta.lore(List.of(LEGACY.deserialize("&7Rends-toi dans la zone AFK pour démarrer le compte à rebours.")));
        } else {
            meta.displayName(LEGACY.deserialize("&b⏰ &fProchaine récompense dans &e" + formatTime(secondsRemaining)));
            meta.lore(List.of(LEGACY.deserialize("&7Reste dans la zone AFK jusque là pour en profiter.")));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static ItemStack buildTokenItem(AfkZoneManager.PendingRewards pending) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&b&lTokens en attente"));
        meta.lore(List.of(
                LEGACY.deserialize("&7Quantité: &f" + pending.getTokens()),
                LEGACY.deserialize("&8"),
                LEGACY.deserialize("&7Gagnés en restant dans la zone AFK.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildMoneyItem(OneBlockPlugin plugin, AfkZoneManager.PendingRewards pending) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        String amount = plugin.getVaultManager().isEnabled()
                ? plugin.getVaultManager().format(pending.getMoney())
                : String.valueOf((long) pending.getMoney());
        meta.displayName(LEGACY.deserialize("&e&lArgent en attente"));
        meta.lore(List.of(
                LEGACY.deserialize("&7Quantité: &f" + amount),
                LEGACY.deserialize("&8"),
                LEGACY.deserialize("&7Gagné en restant dans la zone AFK.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildKeyItem(AfkZoneManager.AfkKeyDef def, int count) {
        ItemStack item = new ItemStack(def.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(def.displayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("&7Quantité en attente: &f" + count));
        lore.add(LEGACY.deserialize("&8"));
        lore.add(LEGACY.deserialize("&7Chance d'obtention: &f" + trimNumber(def.chance()) + "%"));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildClaimItem(boolean hasRewards) {
        ItemStack item = new ItemStack(hasRewards ? Material.EMERALD_BLOCK : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (hasRewards) {
            meta.displayName(LEGACY.deserialize("&a&l✔ Récupérer mes récompenses"));
            meta.lore(List.of(LEGACY.deserialize("&e▶ Clique pour tout récupérer d'un coup !")));
        } else {
            meta.displayName(LEGACY.deserialize("&c&lAucune récompense"));
            meta.lore(List.of(LEGACY.deserialize("&7Reste dans la zone AFK pour en gagner.")));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String trimNumber(double value) {
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
