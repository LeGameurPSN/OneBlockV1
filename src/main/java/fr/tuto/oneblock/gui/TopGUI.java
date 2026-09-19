package fr.tuto.oneblock.gui;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.managers.VaultManager;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * GUI des classements OneBlock (/top) : une rangée d'onglets en haut
 * (tokens, mobs tués, argent perso, caisse d'île, points de renaissance,
 * renaissances totales) et, en dessous, les têtes des joueurs classés dans
 * la catégorie sélectionnée. Cliquer sur un onglet change le classement
 * affiché sans rouvrir l'inventaire (voir GuiListener#handleTopClick).
 */
public class TopGUI {

    public static final String TITLE = "Classements OneBlock";
    public static final String DEFAULT_CATEGORY = "token";

    private static final int SIZE = 54;
    private static final int LIST_START = 18; // début de la zone de classement (rangée 3)
    private static final int LIST_END = 53;   // fin de la zone de classement (dernière rangée)
    private static final int TOP_LIMIT = 10;  // nombre de joueurs affichés par classement

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** Une catégorie de classement : clé, nom affiché, icône, slot de son onglet. */
    private record Category(String key, String label, Material icon, int buttonSlot) {
    }

    private static final Category[] CATEGORIES = {
            new Category("token", "Top Tokens", Material.SUNFLOWER, 10),
            new Category("mob", "Top Mobs tués", Material.IRON_SWORD, 11),
            new Category("bloc", "Top Blocs minés", Material.STONE_PICKAXE, 12),
            new Category("argent", "Top Argent (perso)", Material.EMERALD, 13),
            new Category("banque", "Top Caisse d'île", Material.GOLD_INGOT, 14),
            new Category("rebirthpoints", "Top Points renaissance", Material.NETHER_STAR, 15),
            new Category("rebirths", "Top Renaissances", Material.TOTEM_OF_UNDYING, 16),
    };

    /**
     * Une entrée de classement : le propriétaire (utilisé pour la tête/skull
     * affichée et pour la compatibilité des placeholders existants), la
     * valeur déjà formatée, et le nom de TOUS les membres de l'équipe
     * (propriétaire + coéquipiers) séparés par ", ", pour ne pas afficher
     * uniquement le pseudo du chef d'île dans le classement.
     */
    public record Entry(OfflinePlayer player, String formatted, String teamNames) {
    }

    public static void open(OneBlockPlugin plugin, Player player) {
        open(plugin, player, DEFAULT_CATEGORY);
    }

    public static void open(OneBlockPlugin plugin, Player player, String category) {
        TopGuiHolder holder = new TopGuiHolder(player, category);
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8" + TITLE));
        holder.setInventory(inv);

        fill(plugin, inv, category);
        player.openInventory(inv);
    }

    public static void refresh(OneBlockPlugin plugin, Inventory inv, String category) {
        fill(plugin, inv, category);
    }

    /** Associe chaque slot d'onglet à la catégorie qu'il représente. */
    public static Map<Integer, String> getCategoryButtons() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (Category c : CATEGORIES) {
            map.put(c.buttonSlot(), c.key());
        }
        return map;
    }

    private static void fill(OneBlockPlugin plugin, Inventory inv, String category) {
        inv.clear();

        ItemStack border = namedItemCmd(Material.LIGHT_GRAY_STAINED_GLASS_PANE, 533007, " ");
        for (int i = 0; i < LIST_START; i++) {
            inv.setItem(i, border);
        }

        for (Category c : CATEGORIES) {
            inv.setItem(c.buttonSlot(), buildCategoryItem(c, c.key().equals(category)));
        }

        List<Entry> entries = buildEntries(plugin, category);
        if (entries.isEmpty()) {
            inv.setItem(LIST_START + 4, namedItem(Material.BARRIER, "&cAucune donnée pour le moment"));
            return;
        }

        int slot = LIST_START;
        int rank = 1;
        for (Entry entry : entries) {
            if (slot > LIST_END || rank > TOP_LIMIT) break;
            inv.setItem(slot, buildPlayerItem(rank, entry));
            slot++;
            rank++;
        }
    }

    // ==================== CONSTRUCTION DES CLASSEMENTS ====================

    /**
     * Classement trié (du meilleur au moins bon) pour une catégorie donnée.
     * Rendu public pour être réutilisé par l'expansion PlaceholderAPI
     * (placeholders %oneblock_top_<catégorie>_<position>_...%).
     */
    public static List<Entry> buildEntries(OneBlockPlugin plugin, String category) {
        OneBlockManager manager = plugin.getManager();
        List<IslandData> islands = new ArrayList<>(manager.getAllIslands());

        ToDoubleFunction<IslandData> valueFn;
        Function<IslandData, String> formatFn;

        switch (category) {
            case "token" -> {
                // Chaque joueur a son propre solde de tokens : le classement
                // par île additionne les soldes de toute l'équipe (voir
                // teamNamesOf ci-dessous pour l'affichage nominatif).
                valueFn = IslandData::getTeamTokensTotal;
                formatFn = d -> d.getTeamTokensTotal() + " tokens";
            }
            case "mob" -> {
                valueFn = IslandData::getMobsKilled;
                formatFn = d -> d.getMobsKilled() + " mobs";
            }
            case "bloc" -> {
                // getBrokenCount() sert au niveau OneBlock actuel et est remis à 0
                // à chaque renaissance : on utilise donc getTotalBrokenCount() ici
                // pour que ce classement reflète l'activité réelle (toutes
                // renaissances confondues), sans avantager les îles qui n'ont
                // jamais fait de renaissance.
                valueFn = IslandData::getTotalBrokenCount;
                formatFn = d -> d.getTotalBrokenCount() + " blocs";
            }
            case "argent" -> {
                VaultManager vault = plugin.getVaultManager();
                valueFn = d -> vault.isEnabled() ? vault.getBalance(offlineOf(d)) : 0;
                formatFn = d -> vault.isEnabled() ? vault.format(vault.getBalance(offlineOf(d))) : "?";
            }
            case "banque" -> {
                valueFn = IslandData::getBankBalance;
                formatFn = d -> plugin.getVaultManager().format(d.getBankBalance());
            }
            case "rebirthpoints" -> {
                valueFn = IslandData::getRebirthPoints;
                formatFn = d -> d.getRebirthPoints() + " points";
            }
            case "rebirths" -> {
                valueFn = IslandData::getRebirths;
                formatFn = d -> d.getRebirths() + " renaissances";
            }
            default -> {
                return List.of();
            }
        }

        return islands.stream()
                .sorted(Comparator.comparingDouble(valueFn).reversed())
                .map(d -> new Entry(offlineOf(d), formatFn.apply(d), teamNamesOf(d)))
                .collect(Collectors.toList());
    }

    private static OfflinePlayer offlineOf(IslandData island) {
        return Bukkit.getOfflinePlayer(island.getOwner());
    }

    /**
     * Construit "Chef, Coéquipier1, Coéquipier2" pour une île : le
     * classement doit représenter toute l'équipe, pas seulement son chef.
     * L'ordre d'ajout des coéquipiers (LinkedHashSet dans IslandData) est
     * conservé pour un affichage stable.
     */
    private static String teamNamesOf(IslandData island) {
        StringBuilder sb = new StringBuilder();
        String ownerName = offlineOf(island).getName();
        sb.append(ownerName != null ? ownerName : "?");
        for (java.util.UUID member : island.getMembers()) {
            String name = Bukkit.getOfflinePlayer(member).getName();
            sb.append(", ").append(name != null ? name : "?");
        }
        return sb.toString();
    }

    // ==================== ITEMS ====================

    private static ItemStack buildCategoryItem(Category category, boolean selected) {
        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();

        String prefix = selected ? "&e&l▶ " : "&f";
        meta.displayName(LEGACY.deserialize(prefix + category.label()));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize(selected
                ? "&7Classement actuellement affiché"
                : "&7Clique pour afficher ce classement"));
        meta.lore(lore);

        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildPlayerItem(int rank, Entry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(entry.player());

        String name = entry.player().getName();
        if (name == null) name = entry.player().getUniqueId().toString().substring(0, 8);

        // Le nom affiché reste celui du chef (tête + titre), mais la lore
        // liste TOUTE l'équipe : le classement ne doit pas montrer
        // uniquement le pseudo du chef d'île quand il y a des coéquipiers.
        meta.displayName(LEGACY.deserialize(rankColor(rank) + "#" + rank + " &f" + name));

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("&7" + entry.formatted()));
        if (!entry.teamNames().equals(name)) {
            lore.add(LEGACY.deserialize("&8Équipe: &7" + entry.teamNames()));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static String rankColor(int rank) {
        return switch (rank) {
            case 1 -> "&6"; // or
            case 2 -> "&7"; // argent
            case 3 -> "&c"; // bronze-ish
            default -> "&f";
        };
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
