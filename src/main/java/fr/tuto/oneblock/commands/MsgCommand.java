package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /msg <joueur> <message> — message privé entre deux joueurs connectés.
 * Alias : /tell, /w, /message.
 * Garde en mémoire le dernier interlocuteur de chacun pour permettre /r
 * (répondre au dernier message reçu) sans avoir à retaper le pseudo.
 */
public class MsgCommand implements CommandExecutor, TabCompleter {

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    /** uuid destinataire -> uuid du dernier joueur qui LUI a écrit (pour /r). */
    private final Map<UUID, UUID> lastSenderOf = new HashMap<>();

    public MsgCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isReply = label.equalsIgnoreCase("r") || label.equalsIgnoreCase("reply");

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        Player target;
        String[] messageArgs;

        if (isReply) {
            UUID lastSenderUuid = lastSenderOf.get(player.getUniqueId());
            target = lastSenderUuid != null ? Bukkit.getPlayer(lastSenderUuid) : null;
            if (target == null) {
                msg(player, "&cPersonne ne t'a écrit récemment, ou ce joueur s'est déconnecté.");
                return true;
            }
            if (args.length < 1) {
                msg(player, "&cUsage : /r <message>");
                return true;
            }
            messageArgs = args;
        } else {
            if (args.length < 2) {
                msg(player, "&cUsage : /msg <joueur> <message>");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                msg(player, "&cJoueur \"" + args[0] + "\" introuvable ou hors ligne.");
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                msg(player, "&cTu ne peux pas t'envoyer un message à toi-même.");
                return true;
            }
            messageArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
        }

        String message = String.join(" ", messageArgs);

        msg(player, "&7[Toi &8-> &7" + target.getName() + "&7] &f" + message);
        msg(target, "&7[" + player.getName() + " &8-> &7Toi&7] &f" + message);

        // Mémorise l'interlocuteur pour permettre /r dans les deux sens.
        lastSenderOf.put(target.getUniqueId(), player.getUniqueId());
        lastSenderOf.put(player.getUniqueId(), target.getUniqueId());

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("r") || label.equalsIgnoreCase("reply")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private void msg(CommandSender target, String text) {
        Component c = legacy.deserialize(text);
        target.sendMessage(c);
    }
}
