package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.UUID;

/**
 * /istransfer <ancien_pseudo> <nouveau_pseudo> : commande ADMIN qui transfère
 * la propriété d'une île OneBlock d'un joueur vers un autre.
 *
 * Cas d'usage principal : un joueur a changé de pseudo Mojang. Son île vit
 * toujours dans son monde d'origine ("is_de_<ancien_pseudo>", voir
 * island-world-prefix dans config.yml), mais son nouveau compte a un UUID
 * différent de celui enregistré sur l'île -> sans ce transfert, il perdrait
 * l'accès à son île. Fonctionne aussi pour transférer une île d'un joueur à
 * un autre joueur totalement différent (don d'île, sanction, etc).
 *
 * Le monde et toutes les données de l'île (progression, banque, upgrades...)
 * sont conservés tels quels : seul le propriétaire change.
 */
public class IsTransferCommand implements CommandExecutor, TabCompleter {

    private final OneBlockPlugin plugin;
    private final OneBlockManager manager;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public IsTransferCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("oneblock.admin.istransfer") && !sender.hasPermission("oneblock.admin")) {
            sender.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'utiliser cette commande."));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(legacy.deserialize("&cUsage: &f/istransfer <ancien_pseudo> <nouveau_pseudo>"));
            return true;
        }

        String oldName = args[0];
        String newName = args[1];

        if (oldName.equalsIgnoreCase(newName)) {
            sender.sendMessage(legacy.deserialize("&cL'ancien et le nouveau pseudo sont identiques."));
            return true;
        }

        // 1) Retrouve l'île source. On cherche d'abord par le nom du monde
        // dédié (fiable même si le pseudo a changé chez Mojang depuis), puis
        // en dernier recours via l'UUID actuel de ce pseudo.
        IslandData island = manager.findIslandByWorldPseudo(oldName);
        if (island == null) {
            OfflinePlayer oldPlayer = Bukkit.getOfflinePlayer(oldName);
            island = manager.getIsland(oldPlayer.getUniqueId());
        }
        if (island == null) {
            sender.sendMessage(legacy.deserialize("&cAucune île OneBlock trouvée pour le pseudo &f" + oldName + "&c."));
            return true;
        }

        // 2) Résout le nouveau propriétaire. On exige qu'il ait déjà rejoint
        // le serveur (ou soit en ligne) pour être sûr d'avoir son vrai UUID.
        OfflinePlayer newPlayer = Bukkit.getOfflinePlayer(newName);
        if (!newPlayer.hasPlayedBefore() && !newPlayer.isOnline()) {
            sender.sendMessage(legacy.deserialize(
                    "&cLe joueur &f" + newName + "&c doit s'être connecté au moins une fois au serveur avant de recevoir une île."));
            return true;
        }
        UUID newUuid = newPlayer.getUniqueId();

        if (manager.hasIsland(newUuid)) {
            sender.sendMessage(legacy.deserialize(
                    "&c" + newName + " a déjà une île OneBlock. Utilise &f/ob reset &csur son compte avant de transférer, si tu veux vraiment la remplacer."));
            return true;
        }

        UUID oldUuid = island.getOwner();
        boolean ok = manager.transferIsland(island, newUuid);
        if (!ok) {
            sender.sendMessage(legacy.deserialize("&cLe transfert a échoué (le nouveau joueur a déjà une île)."));
            return true;
        }

        manager.saveIslands();

        sender.sendMessage(legacy.deserialize(
                "&a✔ Île transférée de &f" + oldName + " &a(&7" + oldUuid + "&a) vers &f" + newName + " &a(&7" + newUuid + "&a)."));

        var onlineNew = Bukkit.getPlayer(newUuid);
        if (onlineNew != null) {
            onlineNew.sendMessage(legacy.deserialize("&a✔ Tu as reçu une île OneBlock (transfert admin). Utilise &f/is &apour t'y rendre."));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 || args.length == 2) {
            String lower = args[args.length - 1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(n -> n.toLowerCase().startsWith(lower))
                    .toList();
        }
        return List.of();
    }
}
