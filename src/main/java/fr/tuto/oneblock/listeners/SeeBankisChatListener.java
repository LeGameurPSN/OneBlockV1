package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.commands.SeeBankisCommand;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Intercepte le prochain message de chat d'un admin qui a cliqué sur
 * "[Montant perso]" dans le panneau /seebankis (voir SeeBankisCommand), pour
 * l'utiliser comme montant à ajouter/retirer de la caisse d'île visée -
 * SANS jamais laisser ce message partir au chat public : ni les autres
 * joueurs, ni même le joueur ciblé par /seebankis, ne le voient.
 */
public class SeeBankisChatListener implements Listener {

    private final OneBlockPlugin plugin;
    private final SeeBankisCommand seeBankisCommand;

    public SeeBankisChatListener(OneBlockPlugin plugin, SeeBankisCommand seeBankisCommand) {
        this.plugin = plugin;
        this.seeBankisCommand = seeBankisCommand;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player admin = event.getPlayer();
        if (!seeBankisCommand.hasPending(admin.getUniqueId())) return;

        // Caché de TOUS les autres joueurs (et du serveur/console via le chat public) :
        // seul l'admin lui-même reçoit une confirmation, via applyPendingAmount ci-dessous.
        event.setCancelled(true);

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // La modification de la caisse (+ sauvegarde disque) touche à l'état
        // du jeu : on la fait retourner sur le thread principal, l'événement
        // de chat étant asynchrone.
        Bukkit.getScheduler().runTask(plugin, () -> seeBankisCommand.applyPendingAmount(admin, raw));
    }
}
