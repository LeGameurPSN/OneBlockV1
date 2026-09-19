package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /boost give <x2xp|x2item> <joueur>} : donne à un joueur un item
 * "Cœur de la mer" représentant un boost de 10 minutes (x2 XP ou x2 objets,
 * voir BoostManager), réservé à la permission {@code oneblock.staff.boost}.
 * <p>
 * L'item n'active PAS le boost immédiatement : le joueur doit faire un clic
 * droit dessus pour l'activer (voir BoostItemListener, qui consomme l'item
 * à ce moment-là et démarre le minuteur affiché dans son action bar).
 */
public class BoostCommand implements CommandExecutor, TabCompleter {

    private static final long DURATION_MILLIS = 10L * 60L * 1000L; // 10 minutes

    private static final Map<String, String> TYPE_LABELS = Map.of(
            "x2xp", "&b&lx2 XP",
            "x2item", "&6&lx2 Objets"
    );

    /** Clé PDC posée sur l'item, lue par BoostItemListener au clic droit. */
    public static final String BOOST_TYPE_KEY = "boost_type";

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public BoostCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("oneblock.staff.boost")) {
            msg(sender, "&cTu n'as pas la permission d'utiliser cette commande (&7oneblock.staff.boost&c).");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            msg(sender, "&cUsage : /boost give <x2xp|x2item> <joueur>");
            return true;
        }

        String type = args[1].toLowerCase();
        if (!type.equals("x2xp") && !type.equals("x2item")) {
            msg(sender, "&cType de boost inconnu : &f" + args[1] + "&c. Utilise &fx2xp &cou &fx2item&c.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            msg(sender, "&cJoueur introuvable ou hors ligne : &f" + args[2]);
            return true;
        }

        String typeLabel = TYPE_LABELS.get(type);
        giveHeartOfTheSea(target, type, typeLabel);

        String durationText = fr.tuto.oneblock.managers.BoostManager.formatDuration(DURATION_MILLIS);
        msg(sender, "&a✔ Cœur de la Mer (boost " + typeLabel + "&a) donné à &f" + target.getName()
                + "&a. Il doit faire un clic droit dessus pour l'activer (&f" + durationText + "&a).");
        if (!sender.getName().equalsIgnoreCase(target.getName())) {
            msg(target, "&d&l✦ Un membre du staff t'a offert un &fCœur de la Mer &d&l(boost " + typeLabel + "&d&l) !");
            msg(target, "&7Fais un &fclic droit &7dessus pour l'activer (&f" + durationText + "&7).");
        }
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);

        return true;
    }

    /**
     * Donne au joueur un "Cœur de la mer" représentant le boost à activer.
     * Le type de boost est stocké dans un tag PersistentDataContainer sur
     * l'item, lu par BoostItemListener lors de l'activation (clic droit).
     */
    private void giveHeartOfTheSea(Player target, String type, String typeLabel) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(legacy.deserialize("&b❤ &l Cœur de la Mer"));

        List<Component> lore = new ArrayList<>();
        lore.add(legacy.deserialize("&7Souvenir d'un boost accordé par le staff"));
        lore.add(Component.empty());
        lore.add(legacy.deserialize("&7Boost : " + typeLabel));
        lore.add(legacy.deserialize("&7Durée : &f" + fr.tuto.oneblock.managers.BoostManager.formatDuration(DURATION_MILLIS)));
        lore.add(Component.empty());
        lore.add(legacy.deserialize("&e&l▶ Clic droit pour activer"));
        meta.lore(lore);

        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        NamespacedKey key = new NamespacedKey(plugin, BOOST_TYPE_KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type);

        item.setItemMeta(meta);

        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        for (ItemStack overflow : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), overflow);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("oneblock.staff.boost")) return List.of();

        if (args.length == 1) {
            return filterStartsWith(List.of("give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filterStartsWith(Arrays.asList("x2xp", "x2item"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String partial = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<String> filterStartsWith(List<String> options, String current) {
        String lower = current.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    private void msg(CommandSender target, String text) {
        target.sendMessage(legacy.deserialize(text));
    }
}
