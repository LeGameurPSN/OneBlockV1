package fr.tuto.oneblock.gui;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.models.IslandData;
import fr.tuto.oneblock.models.IslandPermission;
import fr.tuto.oneblock.models.Role;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI de /ob perms (/ob permissions) : système de permissions PAR RÔLE.
 * Trois onglets (ligne du haut) : VISITEUR, TRUST, MEMBRE ; chacun affiche
 * les permissions précises de IslandPermission (jusqu'à 5 lignes de 9 cases,
 * lignes 1 à 5), communes à TOUS les joueurs de ce rôle sur cette île
 * (contrairement à l'ancien système où chaque coéquipier avait ses propres
 * interrupteurs). Réservé au propriétaire de l'île.
 */
public class RolePermissionsGUI {

    public static final String TITLE = "Permissions d'île (rôles)";
    private static final int SIZE = 54;

    private static final int SLOT_TAB_VISITEUR = 0;
    private static final int SLOT_TAB_TRUST = 1;
    private static final int SLOT_TAB_MEMBRE = 2;
    private static final int SLOT_INFO = 4;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final IslandPermission[] PERMS = IslandPermission.values(); // 36 entrées, ordre du GUI

    public static void open(OneBlockPlugin plugin, Player viewer, IslandData island) {
        RolePermissionsGuiHolder holder = new RolePermissionsGuiHolder(viewer, island.getOwner());
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8" + TITLE));
        holder.setInventory(inv);
        fill(inv, island, holder);
        viewer.openInventory(inv);
    }

    public static void refresh(Inventory inv, IslandData island, RolePermissionsGuiHolder holder) {
        fill(inv, island, holder);
    }

    private static void fill(Inventory inv, IslandData island, RolePermissionsGuiHolder holder) {
        inv.clear();

        ItemStack border = namedItemCmd(Material.GRAY_STAINED_GLASS_PANE, 533004, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        Role active = holder.getActiveRole();
        inv.setItem(SLOT_TAB_VISITEUR, tabItem(Material.IRON_BARS, Role.VISITEUR, active == Role.VISITEUR));
        inv.setItem(SLOT_TAB_TRUST, tabItem(Material.TOTEM_OF_UNDYING, Role.TRUST, active == Role.TRUST));
        inv.setItem(SLOT_TAB_MEMBRE, tabItem(Material.PLAYER_HEAD, Role.MEMBRE, active == Role.MEMBRE));

        inv.setItem(SLOT_INFO, namedItem(Material.BOOK, "&e&lPermissions du rôle : " + active.getDisplayName(),
                active.getDescription(),
                "&8",
                "&7Ces réglages s'appliquent à TOUS les",
                "&7joueurs ayant ce rôle sur cette île.",
                "&8",
                "&7Clique sur un interrupteur pour",
                "&7autoriser / refuser une action."));

        for (int i = 0; i < PERMS.length; i++) {
            IslandPermission perm = PERMS[i];
            boolean value = island.getRolePermission(active, perm.getKey());
            int slot = 9 + i; // lignes 1 à 4 : slots 9..44
            inv.setItem(slot, buildToggleItem(perm, value));
        }
    }

    /** Résout la permission correspondant à un slot cliqué (lignes 1 à 4), ou null si ce n'est pas une permission. */
    public static IslandPermission resolveClick(int slot) {
        if (slot < 9 || slot >= 9 + PERMS.length) return null;
        return PERMS[slot - 9];
    }

    public static boolean isTabVisiteurSlot(int slot) {
        return slot == SLOT_TAB_VISITEUR;
    }

    public static boolean isTabTrustSlot(int slot) {
        return slot == SLOT_TAB_TRUST;
    }

    public static boolean isTabMembreSlot(int slot) {
        return slot == SLOT_TAB_MEMBRE;
    }

    private static ItemStack tabItem(Material icon, Role role, boolean active) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize((active ? "&l▶ " : "") + role.getDisplayName()));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize(role.getDescription()));
        lore.add(LEGACY.deserialize("&8"));
        lore.add(LEGACY.deserialize(active ? "&a✔ Onglet actif" : "&e▶ Clique pour afficher"));
        meta.lore(lore);
        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildToggleItem(IslandPermission perm, boolean value) {
        ItemStack item = new ItemStack(perm.getIcon());
        ItemMeta meta = item.getItemMeta();
        String status = value ? "&a✔ " + perm.getLabel() : "&c✘ " + perm.getLabel();
        meta.displayName(LEGACY.deserialize(status));
        meta.lore(List.of(
                LEGACY.deserialize(value ? "&7Autorisé" : "&7Refusé"),
                LEGACY.deserialize("&8"),
                LEGACY.deserialize(value ? "&e▶ Clique pour retirer" : "&e▶ Clique pour autoriser")
        ));
        if (value) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack namedItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        if (loreLines.length > 0) {
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(LEGACY.deserialize(line));
            }
            meta.lore(lore);
        }
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
