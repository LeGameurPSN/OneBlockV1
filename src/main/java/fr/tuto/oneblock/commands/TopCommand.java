package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.gui.TopGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class TopCommand implements CommandExecutor, TabCompleter {

    private final OneBlockPlugin plugin;
    private static final List<String> CATEGORIES = List.of(
            "token", "mob", "bloc", "argent", "banque", "rebirthpoints", "rebirths"
    );

    public TopCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("oneblock.top")) {
            player.sendMessage("§cTu n'as pas la permission d'utiliser /top.");
            return true;
        }
        String category = (args.length > 0 && CATEGORIES.contains(args[0].toLowerCase()))
                ? args[0].toLowerCase()
                : TopGUI.DEFAULT_CATEGORY;

        String categoryNode = "oneblock.top." + category;
        if (!player.hasPermission(categoryNode)) {
            player.sendMessage("§cTu n'as pas la permission de consulter ce classement (§7" + categoryNode + "§c).");
            return true;
        }

        TopGUI.open(plugin, player, category);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return CATEGORIES.stream()
                    .filter(c -> c.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
