package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Intercepte le chat classique des joueurs ayant activé le mode "chat d'équipe"
 * (/ob chat) pour ne l'envoyer qu'au propriétaire et aux coéquipiers en ligne
 * de leur île, à la place du chat public.
 */
public class TeamChatListener implements Listener {

    private final OneBlockPlugin plugin;
    private final OneBlockManager manager;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public TeamChatListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!manager.isTeamChatEnabled(uuid)) return;

        IslandData island = manager.getIsland(uuid);
        if (island == null) {
            // Sécurité : plus d'île (ex: kick) -> on désactive le mode et le
            // message repart normalement en chat public.
            manager.setTeamChatEnabled(uuid, false);
            return;
        }

        // On empêche totalement l'envoi au chat public.
        event.setCancelled(true);

        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        Component formatted = legacy.deserialize("&8[&aÉquipe&8] &f" + player.getName() + "&7: &f" + plainMessage);

        Set<Player> recipients = new LinkedHashSet<>();
        Player owner = plugin.getServer().getPlayer(island.getOwner());
        if (owner != null) recipients.add(owner);
        for (UUID memberUuid : island.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberUuid);
            if (member != null) recipients.add(member);
        }
        recipients.add(player); // Le joueur voit toujours son propre message.

        for (Player recipient : recipients) {
            recipient.sendMessage(formatted);
        }
    }
}
