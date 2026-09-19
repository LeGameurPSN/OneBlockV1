package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Intercepte (en lecture seule, MONITOR) chaque message de chat pour vérifier
 * s'il correspond à la réponse de la question trivia actuellement posée
 * (voir {@link fr.tuto.oneblock.managers.TriviaManager}). Ne modifie ni
 * n'annule jamais le message : il reste visible normalement dans le chat.
 */
public class TriviaChatListener implements Listener {

    private final OneBlockPlugin plugin;

    public TriviaChatListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getTriviaManager().tryAnswer(player, plainMessage);
    }
}
