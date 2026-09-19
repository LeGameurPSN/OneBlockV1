package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.models.IslandData;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Corrige le fait que la WorldBorder personnelle "disparaisse" après une mort :
 * par défaut le joueur respawn au spawn du monde (souvent hors de sa bordure,
 * ce qui donne l'impression que la bordure a sauté). On le fait toujours
 * respawn sur son île et on réapplique explicitement sa bordure.
 */
public class PlayerRespawnListener implements Listener {

    private final OneBlockPlugin plugin;

    public PlayerRespawnListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island == null) return;

        Location respawnLoc = island.getBlockLocation().clone().add(0.5, 1.0, 0.5);
        event.setRespawnLocation(respawnLoc);

        // La bordure est réappliquée un tick plus tard pour être sûr que le
        // joueur soit bien téléporté avant que le client ne la reçoive.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    plugin.getManager().applyBorder(player, island);
                    plugin.getManager().applyTimeLock(player, island);
                    boolean fly = island.getSetting(player.getWorld(), "fly", false);
                    player.setAllowFlight(fly);
                    plugin.getBossBarManager().update(player, island);
                }
            }
        }.runTaskLater(plugin, 1L);
    }
}
