package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /cinetest — rejoue la cinématique d'arrivée (celle des nouveaux joueurs)
 * en mode test, pour vérifier/ajuster les messages et les points de
 * téléportation sans avoir à créer un compte joueur vierge.
 * <p>
 * Réservée à LeGameurPSN_YT (ou aux opérateurs du serveur, pour pouvoir la
 * tester facilement en développement). En mode test, la scène finale ne crée
 * JAMAIS d'île si le testeur en a déjà une : elle se contente de l'y
 * téléporter, exactement comme le ferait un joueur qui a déjà son île.
 */
public class CineTestCommand implements CommandExecutor {

    /** Pseudo autorisé à utiliser /cinetest en plus des opérateurs. */
    private static final String ALLOWED_PLAYER = "LeGameurPSN_YT";

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public CineTestCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        if (!player.getName().equalsIgnoreCase(ALLOWED_PLAYER)) {
            player.sendMessage(legacy.deserialize("&cCette commande est réservée à " + ALLOWED_PLAYER + "."));
            return true;
        }

        if (!plugin.getCinematicManager().isEnabled()) {
            player.sendMessage(legacy.deserialize(
                    "&cLa cinématique d'arrivée est désactivée dans la config (intro-cinematic.enabled: false)."));
            return true;
        }

        player.sendMessage(legacy.deserialize("&7[Test] Lancement de la cinématique d'arrivée..."));
        plugin.getCinematicManager().playIntro(player, true);
        return true;
    }
}
