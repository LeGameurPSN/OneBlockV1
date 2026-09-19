package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.gui.AfkGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AfkCommand implements CommandExecutor {

    private final OneBlockPlugin plugin;

    public AfkCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("oneblock.afk")) {
            player.sendMessage("§cTu n'as pas la permission d'utiliser /afk.");
            return true;
        }

        // /afk debug : diagnostic (monde + coordonnées) pour comprendre
        // pourquoi la zone AFK ne se déclenche pas malgré une présence sur
        // place (mauvais nom de monde configuré, coordonnées mal calées...).
        if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
            var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
            for (String line : plugin.getAfkZoneManager().debugInfo(player.getLocation())) {
                player.sendMessage(legacy.deserialize(line));
            }
            return true;
        }

        AfkGUI.open(plugin, player);
        return true;
    }
}
