package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final OneBlockPlugin plugin;

    public PlayerQuitListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getBossBarManager().remove(event.getPlayer());
        plugin.getManager().stopBorderParticles(event.getPlayer());
        // On désactive le mode chat d'équipe à la déconnexion, pour repartir
        // proprement en chat public à la reconnexion.
        plugin.getManager().setTeamChatEnabled(event.getPlayer().getUniqueId(), false);
    }
}
