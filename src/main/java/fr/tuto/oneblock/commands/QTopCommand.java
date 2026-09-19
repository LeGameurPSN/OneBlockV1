package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.models.PlayerTriviaStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /qtop [pseudo] : affiche le classement des meilleures séries de bonnes
 * réponses au trivia (voir {@link fr.tuto.oneblock.managers.TriviaManager}),
 * ainsi que la série actuelle et le nombre de bonnes/mauvaises réponses du
 * joueur (soi-même par défaut, ou un autre joueur donné en argument).
 */
public class QTopCommand implements CommandExecutor, TabCompleter {

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public QTopCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("oneblock.qtop")) {
            sender.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'utiliser /qtop."));
            return true;
        }

        OfflinePlayer target;
        if (args.length > 0) {
            target = Bukkit.getOfflinePlayer(args[0]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(legacy.deserialize("&cPrécise un pseudo : /qtop <pseudo>"));
            return true;
        }

        var manager = plugin.getTriviaManager();
        PlayerTriviaStats stats = manager.getStats(target);
        String displayName = target.getName() != null ? target.getName() : args[0];

        send(sender, "");
        send(sender, "&6&l✦ &e&lTOP QUESTIONS BONUS &6&l✦");
        send(sender, "");
        send(sender, "&7Stats de &f" + displayName + "&7 :");
        send(sender, "  &e➤ &7Série actuelle : &f" + stats.getCurrentStreak());
        send(sender, "  &e➤ &7Meilleure série : &f" + stats.getBestStreak());
        send(sender, "  &a➤ &7Bonnes réponses : &f" + stats.getCorrectAnswers());
        send(sender, "  &c➤ &7Mauvaises réponses : &f" + stats.getWrongAnswers());
        send(sender, "");

        List<PlayerTriviaStats> top = manager.getTopByBestStreak(5);
        if (!top.isEmpty()) {
            send(sender, "&7Classement des meilleures séries :");
            int rank = 1;
            for (PlayerTriviaStats entry : top) {
                send(sender, "  &6#" + rank + " &f" + entry.getLastKnownName()
                        + " &7- série max &e" + entry.getBestStreak()
                        + " &7(&a" + entry.getCorrectAnswers() + "&7/&c" + entry.getWrongAnswers() + "&7)");
                rank++;
            }
            send(sender, "");
        }

        return true;
    }

    private void send(CommandSender sender, String text) {
        Component c = legacy.deserialize(text);
        sender.sendMessage(c);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
