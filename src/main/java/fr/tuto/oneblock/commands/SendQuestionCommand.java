package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /sendquestion — force l'envoi immédiat d'une question bonus dans le chat,
 * en avance sur le minuteur normal (voir trivia.interval-minutes). Réservée
 * à LeGameurPSN_YT (ou aux opérateurs, pour pouvoir tester facilement).
 */
public class SendQuestionCommand implements CommandExecutor {

    private static final String ALLOWED_PLAYER = "LeGameurPSN_YT";

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public SendQuestionCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !player.getName().equalsIgnoreCase(ALLOWED_PLAYER)) {
            player.sendMessage(legacy.deserialize("&cCette commande est réservée à " + ALLOWED_PLAYER + "."));
            return true;
        }

        if (!plugin.getTriviaManager().isEnabled()) {
            sender.sendMessage("§cLe système de questions est désactivé dans la config (trivia.enabled: false).");
            return true;
        }

        boolean sent = plugin.getTriviaManager().askQuestion();
        if (sent) {
            sender.sendMessage("§aQuestion envoyée dans le chat !");
        } else {
            sender.sendMessage("§cImpossible : une question est déjà en cours, ou aucun joueur n'est connecté.");
        }
        return true;
    }
}
