package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /chelp — aide condensée destinée aux nouveaux joueurs : reprend toutes les
 * commandes /ob utiles au quotidien (île, équipe, boutiques...) ainsi que
 * /kit et /kits. Contrairement à /ob (qui liste TOUTES les sous-commandes,
 * y compris avancées), celle-ci se veut plus courte et accueillante.
 */
public class ChelpCommand implements CommandExecutor {

    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public ChelpCommand(OneBlockPlugin plugin) {
        // plugin non utilisé pour l'instant, gardé pour cohérence avec les autres commandes
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        msg(sender, "&6&l=== Guide du nouveau joueur ===");
        msg(sender, "&7Bienvenue ! Voici les commandes essentielles pour démarrer.");
        msg(sender, "");
        msg(sender, "&6--- Ton île OneBlock ---");
        msg(sender, "&f/ob start &7- Crée ton île OneBlock");
        msg(sender, "&f/ob tp &7&f(ou /is) &7- Retourne sur ton île");
        msg(sender, "&f/ob niveau &7- Affiche ta progression");
        msg(sender, "&f/ob agrandir &7- Agrandit ta bordure d'île (coûte de l'argent)");
        msg(sender, "&f/ob settings &7- Ouvre le GUI des paramètres de ton île (mobs, pvp...)");
        msg(sender, "&f/ob visit <pseudo> &7&f(ou /is visit <pseudo>) &7- Visite l'île d'un autre joueur");
        msg(sender, "&f/ob reset &7- Réinitialise entièrement ton île");
        msg(sender, "&f/ob renaissance &7&f(ou /rebirth) &7- Repars niveau 1 depuis \"Fin du Monde\" (garde tes renaissances)");
        msg(sender, "&f/ob lock &7&f/ &f/ob unlock &7- Verrouille/déverrouille ton île aux visiteurs");
        msg(sender, "");
        msg(sender, "&6--- Équipe ---");
        msg(sender, "&f/ob team &7- Affiche ton équipe");
        msg(sender, "&f/ob team invite <pseudo> &7- Invite un joueur dans ton équipe");
        msg(sender, "&f/ob team accept &7&f/ &f/ob team deny &7- Accepte/refuse une invitation reçue");
        msg(sender, "&f/ob trust <pseudo> &7- Autorise un joueur à construire/casser sur ton île");
        msg(sender, "&f/ob untrust <pseudo> &7- Retire un joueur de la liste de confiance");
        msg(sender, "&f/ob kick <pseudo> &7- Exclut un coéquipier &8(propriétaire)");
        msg(sender, "&f/ob ban <pseudo> &7&f/ &f/ob unban <pseudo> &7- Bannit/débannit un joueur de ton île &8(propriétaire)");
        msg(sender, "&f/ob leave &7- Quitte l'équipe que tu as rejointe");
        msg(sender, "&f/ob chat <message> &7- Chat d'équipe &7&f(sans argument : active/désactive)");
        msg(sender, "");
        msg(sender, "&6--- Économie & boutiques ---");
        msg(sender, "&f/bankis deposer <montant> &7&f/ &f/bankis retirer <montant> &7- Dépôt/retrait dans la caisse d'île");
        msg(sender, "&f/bankis objets &7- Coffre d'objets partagé de l'île");
        msg(sender, "&f/token shop &7- Ouvre la boutique à tokens");
        msg(sender, "&f/token buy fly &7- Achète le vol sur ton île avec tes tokens");
        msg(sender, "&f/upgrades &7- Améliorations d'île (avec ton argent)");
        msg(sender, "&f/rebirthshop &7&f(ou /rbshop) &7- Boutique de renaissance (points de renaissance)");
        msg(sender, "&f/top <token|mob|argent|banque|rebirthpoints|rebirths> &7- Classements du serveur");
        msg(sender, "&f/afk &7- Récompenses de la zone AFK");
        msg(sender, "");
        msg(sender, "&6--- Kits ---");
        msg(sender, "&f/kits &7- Liste les kits disponibles");
        msg(sender, "&f/kit <nom> &7- Réclame un kit");
        msg(sender, "");
        msg(sender, "&6--- Communication ---");
        msg(sender, "&f/msg <joueur> <message> &7&f(ou /tell, /w) &7- Envoie un message privé à un joueur");
        msg(sender, "&f/r <message> &7- Répond au dernier message privé reçu");
        msg(sender, "");
        msg(sender, "&6--- Divers ---");
        msg(sender, "&f/spawn &7- Retourne au spawn du serveur");
        msg(sender, "&f/ob &7- Liste complète de toutes les commandes OneBlock");
        msg(sender, "");
        msg(sender, "&e&l⚡ Garde un œil sur le chat : des questions bonus (calcul, capitales/pays) tombent régulièrement et rapportent de l'argent au plus rapide !");
        return true;
    }

    private void msg(CommandSender sender, String text) {
        Component c = legacy.deserialize(text);
        sender.sendMessage(c);
    }
}
