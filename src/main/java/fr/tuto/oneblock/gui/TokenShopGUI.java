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
import java.util.List;
import java.util.Map;

/**
 * GUI de la boutique à tokens (/token) : affiche les articles achetables
 * avec leur prix, et l'état (déjà possédé / achetable / tokens
 * insuffisants). Le système (ITEMS + slots) est prévu pour accueillir
 * facilement de nouveaux articles.
 */
public class TokenShopGUI {

    public static final String TITLE = "Boutique - Tokens";
    private static final int SIZE = 27;
    private static final int BALANCE_SLOT = 4;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** Un article de la boutique. */
    public record ShopItem(String key, String displayName, Material icon, List<String> description) {
    }

    // Pour ajouter un article: ajouter une entrée ici + le coût dans config.yml
    // (token.<key>-cost), + le traitement de l'achat dans GuiListener.
    private static final ShopItem[] ITEMS = {
            new ShopItem("fly", "Fly IS", Material.ELYTRA,
                    List.of("Autorise le vol sur ta propre île.")),
            new ShopItem("hat", "Accès à la commande /hat", Material.GOLDEN_HELMET,
                    List.of("Après achat, vous obtiendrez",
                            "l'accès à cette commande !",
                            "",
                            "Sur la tête :",
                            "+2 en points d'armure")),
            new ShopItem("feed", "Accès à la commande /feed", Material.CAKE,
                    List.of("Après achat, vous obtiendrez",
                            "l'accès à cette commande !")),
            new ShopItem("cle_commune", "Clé Commune", Material.TRIPWIRE_HOOK,
                    List.of("Après achat, vous recevrez",
                            "exactement cet objet !")),
            new ShopItem("cle_magique", "Clé Magique", Material.TRIPWIRE_HOOK,
                    List.of("Après achat, vous recevrez",
                            "exactement cet objet !")),
            new ShopItem("balise", "Balise", Material.BEACON,
                    List.of("Après achat, vous recevrez",
                            "exactement cet objet !")),
            new ShopItem("argent", "Argent", Material.HAY_BLOCK,
                    List.of("Après achat, vous recevrez",
                            "ce montant d'argent !"))
    };

    // Articles consommables : achetables plusieurs fois (pas de déblocage
    // définitif). Contrairement à "fly"/"hat"/"feed", isUnlocked() renvoie
    // toujours false pour ces clés afin que l'achat reste possible à l'infini.
    private static final java.util.Set<String> CONSUMABLE_KEYS = java.util.Set.of("cle_commune", "cle_magique", "balise", "argent");

    public static boolean isConsumable(String key) {
        return CONSUMABLE_KEYS.contains(key);
    }

    // Slots centraux de la rangée du milieu, un par article (jusqu'à 7).
    private static final int[] CONTENT_SLOTS = {11, 12, 13, 14, 15, 16, 17};

    private static final Map<String, Integer> SLOTS = new LinkedHashMap<>();
    static {
        for (int i = 0; i < ITEMS.length && i < CONTENT_SLOTS.length; i++) {
            SLOTS.put(ITEMS[i].key(), CONTENT_SLOTS[i]);
        }
    }

    public static Map<String, Integer> getSlots() {
        return SLOTS;
    }

    public static ShopItem getItem(String key) {
        for (ShopItem item : ITEMS) {
            if (item.key().equals(key)) return item;
        }
        return null;
    }

    public static void open(OneBlockPlugin plugin, Player player, IslandData island) {
        TokenShopGuiHolder holder = new TokenShopGuiHolder(player);
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8" + TITLE));
        holder.setInventory(inv);

        fill(plugin, inv, player, island);
        player.openInventory(inv);
    }

    public static void refresh(OneBlockPlugin plugin, Inventory inv, Player buyer, IslandData island) {
        fill(plugin, inv, buyer, island);
    }

    private static void fill(OneBlockPlugin plugin, Inventory inv, Player buyer, IslandData island) {
        inv.clear();

        ItemStack border = namedItemCmd(Material.MAGENTA_STAINED_GLASS_PANE, 533006, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(BALANCE_SLOT, buildBalanceItem(buyer, island));

        int balance = island.getTokens(buyer.getUniqueId());
        for (ShopItem shopItem : ITEMS) {
            Integer slot = SLOTS.get(shopItem.key());
            if (slot == null) continue;
            int cost = getCost(plugin, shopItem.key());
            boolean unlocked = isUnlocked(island, shopItem.key());
            inv.setItem(slot, buildShopItem(shopItem, cost, unlocked, balance));
        }
    }

    /** Coût configuré pour un article donné (token.<key>-cost dans token.yml). */
    public static int getCost(OneBlockPlugin plugin, String key) {
        return plugin.getTokenConfig().getInt("token." + key + "-cost", 1000);
    }

    /** Indique si l'île possède déjà l'article donné. */
    public static boolean isUnlocked(IslandData island, String key) {
        return switch (key) {
            case "fly" -> island.isFlyUnlocked();
            case "hat" -> island.isHatUnlocked();
            case "feed" -> island.isFeedUnlocked();
            case "cle_commune" -> false; // consommable : jamais "débloqué", toujours achetable
            case "cle_magique" -> false; // consommable : jamais "débloqué", toujours achetable
            case "balise" -> false; // consommable : jamais "débloqué", toujours achetable
            case "argent" -> false; // consommable : jamais "débloqué", toujours achetable
            default -> false;
        };
    }

    /**
     * Item de solde : le nombre de tokens PERSONNEL de l'acheteur en titre,
     * et si l'île a une équipe, le détail nominatif de chaque coéquipier
     * (nom + son propre solde) ainsi que le total combiné en lore -
     * chacun garde ses propres tokens, mais on voit toute l'équipe "d'un
     * coup d'œil" dans un seul item.
     */
    private static ItemStack buildBalanceItem(Player buyer, IslandData island) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&b&lTon solde: &f" + island.getTokens(buyer.getUniqueId()) + " tokens"));

        List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(LEGACY.deserialize("&7Les tokens s'obtiennent via le staff"));
        lore.add(LEGACY.deserialize("&7(votes, events, boutique...)."));

        Map<java.util.UUID, Integer> breakdown = island.getTeamTokensBreakdown();
        if (breakdown.size() > 1) {
            lore.add(LEGACY.deserialize(" "));
            lore.add(LEGACY.deserialize("&b&lÉquipe :"));
            for (Map.Entry<java.util.UUID, Integer> entry : breakdown.entrySet()) {
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer member = Bukkit.getOfflinePlayer(entry.getKey());
                String name = member.getName() != null ? member.getName() : "?";
                boolean self = entry.getKey().equals(buyer.getUniqueId());
                lore.add(LEGACY.deserialize((self ? "&e▶ &f" : "&7- &f") + name + " &7: &e" + entry.getValue()));
            }
            lore.add(LEGACY.deserialize("&7Total équipe : &e" + island.getTeamTokensTotal() + " tokens"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildShopItem(ShopItem shopItem, int cost, boolean unlocked, int balance) {
        ItemStack item = new ItemStack(unlocked ? shopItem.icon() : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        for (String line : shopItem.description()) {
            lore.add(LEGACY.deserialize("&7" + line));
        }
        lore.add(LEGACY.deserialize("&8"));

        String name;
        if (unlocked) {
            name = "&a✔ " + shopItem.displayName();
            lore.add(LEGACY.deserialize("&aDéjà débloqué !"));
        } else {
            name = "&d✦ " + shopItem.displayName();
            lore.add(LEGACY.deserialize("&7Prix: &b" + cost + " tokens"));
            if (balance >= cost) {
                lore.add(LEGACY.deserialize("&e▶ Clique pour acheter"));
            } else {
                lore.add(LEGACY.deserialize("&c✘ Tokens insuffisants (&f" + balance + "&c/&f" + cost + "&c)"));
            }
        }
        meta.displayName(LEGACY.deserialize(name));
        meta.lore(lore);

        if (unlocked) {
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
