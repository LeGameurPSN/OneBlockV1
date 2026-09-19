package fr.tuto.oneblock.gui;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI de {@code /ob menu} : un moyen rapide de visiter/faire confiance/
 * bannir un joueur en cliquant simplement sur sa tête, sans taper de
 * commande. Une rangée d'onglets en haut ("Visite" / "Sanction") choisit
 * quelles actions sont disponibles pour les clics gauche/droit (et
 * shift+clic) sur les têtes affichées en dessous (voir GuiListener pour le
 * détail des actions, qui délèguent toutes aux sous-commandes /ob
 * existantes pour garder exactement les mêmes règles/permissions).
 */
public class OBMenuGUI {

    public static final String TITLE = "Menu OneBlock";
    private static final int SIZE = 54;

    public static final int TAB_VISITE_SLOT = 2;
    public static final int TAB_SANCTION_SLOT = 6;
    private static final int INFO_SLOT = 4;

    private static final int LIST_START = 9;
    private static final int LIST_END = 53;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static void open(OneBlockPlugin plugin, Player viewer, OBMenuGuiHolder.Category category) {
        OBMenuGuiHolder holder = new OBMenuGuiHolder(viewer, category);
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8" + TITLE));
        holder.setInventory(inv);

        fill(plugin, inv, viewer, holder);
        viewer.openInventory(inv);
    }

    public static void refresh(OneBlockPlugin plugin, Inventory inv, Player viewer, OBMenuGuiHolder holder) {
        fill(plugin, inv, viewer, holder);
    }

    private static void fill(OneBlockPlugin plugin, Inventory inv, Player viewer, OBMenuGuiHolder holder) {
        inv.clear();

        ItemStack border = namedItemCmd(Material.LIGHT_GRAY_STAINED_GLASS_PANE, 533002, " ");
        for (int i = 0; i < LIST_START; i++) {
            inv.setItem(i, border);
        }

        boolean visite = holder.getCategory() == OBMenuGuiHolder.Category.VISITE;
        inv.setItem(TAB_VISITE_SLOT, buildTabItem(Material.COMPASS, "&b&lVisite", visite,
                "&7Clique gauche &f-> &7visiter son île",
                "&7Clique droit &f-> &7faire confiance (&fTRUST&7)",
                "&7Shift + clic droit &f-> &7retirer la confiance"));
        inv.setItem(TAB_SANCTION_SLOT, buildTabItem(Material.BARRIER, "&c&lSanction", !visite,
                "&7Clique gauche &f-> &7bannir de ton île",
                "&7Clique droit &f-> &7débannir",
                "&7Shift + clic gauche &f-> &7exclure un coéquipier"));
        inv.setItem(INFO_SLOT, buildInfoItem());

        Map<Integer, UUID> slotTargets = new LinkedHashMap<>();
        List<OfflinePlayer> targets = buildTargetList(plugin, viewer);

        int slot = LIST_START;
        for (OfflinePlayer target : targets) {
            if (slot > LIST_END) break;
            inv.setItem(slot, buildPlayerItem(target, holder.getCategory()));
            slotTargets.put(slot, target.getUniqueId());
            slot++;
        }
        holder.setSlotTargets(slotTargets);
    }

    /**
     * Liste des joueurs proposés dans le menu : tous les joueurs connectés,
     * tous les propriétaires d'île connus (même hors-ligne), plus les
     * coéquipiers/joueurs de confiance de ta propre île (nécessaires pour
     * pouvoir les exclure/leur retirer ta confiance même s'ils ne sont pas
     * eux-mêmes propriétaires d'île) — le tout dédoublonné, trié en ligne
     * d'abord puis par ordre alphabétique, sans le joueur lui-même.
     */
    private static List<OfflinePlayer> buildTargetList(OneBlockPlugin plugin, Player viewer) {
        OneBlockManager manager = plugin.getManager();
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            uuids.add(online.getUniqueId());
        }
        for (IslandData island : manager.getAllIslands()) {
            if (island.getOwner() != null) uuids.add(island.getOwner());
        }
        IslandData myIsland = manager.getIsland(viewer.getUniqueId());
        if (myIsland != null) {
            uuids.addAll(myIsland.getMembers());
            uuids.addAll(myIsland.getTrustedPlayers());
        }
        uuids.remove(viewer.getUniqueId());

        List<OfflinePlayer> players = new ArrayList<>();
        for (UUID uuid : uuids) {
            players.add(Bukkit.getOfflinePlayer(uuid));
        }

        players.sort(Comparator
                .comparing((OfflinePlayer p) -> !p.isOnline())
                .thenComparing(p -> p.getName() == null ? "" : p.getName(), String.CASE_INSENSITIVE_ORDER));

        return players;
    }

    // ==================== ITEMS ====================

    private static ItemStack buildTabItem(Material material, String label, boolean selected, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String prefix = selected ? "&e&l▶ " : "";
        meta.displayName(LEGACY.deserialize(prefix + label));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize(selected ? "&7Catégorie active" : "&7Clique pour sélectionner"));
        lore.add(Component.empty());
        for (String line : loreLines) {
            lore.add(LEGACY.deserialize(line));
        }
        meta.lore(lore);

        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&6&lMenu OneBlock"));
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("&7Choisis une catégorie en haut, puis"));
        lore.add(LEGACY.deserialize("&7clique sur un joueur pour agir sur lui."));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildPlayerItem(OfflinePlayer target, OBMenuGuiHolder.Category category) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);

        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
        meta.displayName(LEGACY.deserialize("&f" + name));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize(target.isOnline() ? "&aEn ligne" : "&8Hors ligne"));
        lore.add(Component.empty());
        if (category == OBMenuGuiHolder.Category.VISITE) {
            lore.add(LEGACY.deserialize("&7Clique gauche &f-> &7visiter son île"));
            lore.add(LEGACY.deserialize("&7Clique droit &f-> &7faire confiance (&fTRUST&7)"));
            lore.add(LEGACY.deserialize("&7Shift + clic droit &f-> &7retirer la confiance"));
        } else {
            lore.add(LEGACY.deserialize("&7Clique gauche &f-> &7bannir de ton île"));
            lore.add(LEGACY.deserialize("&7Clique droit &f-> &7débannir"));
            lore.add(LEGACY.deserialize("&7Shift + clic gauche &f-> &7exclure un coéquipier"));
        }
        meta.lore(lore);

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
