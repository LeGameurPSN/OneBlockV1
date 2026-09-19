package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * /staffchat <message> — envoie un message uniquement visible par les
 * joueurs (et la console) possédant la permission oneblock.staff.chat.
 * Format : &8[&cStaffChat&8] &f<joueur> &8: &f<message>
 */
public class StaffChatCommand implements CommandExecutor {

    private static final String PERMISSION = "oneblock.staff.chat";

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public StaffChatCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            msg(sender, "&cTu n'as pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length < 1) {
            msg(sender, "&cUsage : /staffchat <message>");
            return true;
        }

        String senderName = sender instanceof Player player ? player.getName() : "Console";
        String message = String.join(" ", args);
        Component formatted = legacy.deserialize("&8[&cStaffChat&8] &f" + senderName + " &8: &f" + message);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(PERMISSION)) {
                online.sendMessage(formatted);
            }
        }

        ConsoleCommandSender console = Bukkit.getConsoleSender();
        if (!(sender instanceof ConsoleCommandSender)) {
            console.sendMessage(formatted);
        }

        return true;
    }

    private void msg(CommandSender target, String text) {
        target.sendMessage(legacy.deserialize(text));
    }
}
