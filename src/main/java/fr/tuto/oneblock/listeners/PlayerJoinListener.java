package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.models.IslandData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * À chaque connexion, le joueur est téléporté au spawn du serveur
 * (server-spawn dans config.yml, par défaut x:0 y:200 z:0) plutôt que sur
 * son île OneBlock. Il devra utiliser /ob tp pour retourner sur son île.
 */
public class PlayerJoinListener implements Listener {

    private final OneBlockPlugin plugin;

    public PlayerJoinListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Tout nouveau joueur (jamais connecté ET pas encore d'île) : on lui joue la
        // cinématique d'arrivée à la place du reste de cette méthode. C'est elle qui
        // s'occupe de la téléportation initiale, de la visite du spawn, puis de la
        // création de l'île et du téléport final dessus.
        boolean brandNewPlayer = !player.hasPlayedBefore() && !plugin.getManager().hasIsland(player.getUniqueId());
        if (brandNewPlayer && plugin.getCinematicManager().isEnabled()) {
            player.setWorldBorder(null);
            plugin.getManager().stopBorderParticles(player);
            plugin.getCinematicManager().playIntro(player, false);
            return;
        }

        Location spawnLoc = plugin.getManager().getServerSpawnLocation();
        if (spawnLoc == null) {
            plugin.getLogger().warning("server-spawn.world introuvable, impossible de téléporter "
                    + player.getName() + " au spawn à sa connexion.");
            return;
        }

        // La bordure OneBlock n'a pas de sens au spawn du serveur (potentiellement
        // un autre monde) : on la retire tant que le joueur n'est pas retourné
        // sur son île via /ob tp.
        player.setWorldBorder(null);
        plugin.getManager().stopBorderParticles(player);

        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.setAllowFlight(false);
            if (player.isFlying()) player.setFlying(false);
        }
        player.resetPlayerTime();

        player.teleport(spawnLoc);

        // Ré-attache la permission /hat si elle a été achetée dans /token
        // (les PermissionAttachment ne survivent pas à une déconnexion).
        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island != null && island.isHatUnlocked()) {
            player.addAttachment(plugin, "essentials.hat", true);
        }
        // Ré-attache la permission /feed si elle a été achetée dans /token
        // (les PermissionAttachment ne survivent pas à une déconnexion).
        if (island != null && island.isFeedUnlocked()) {
            player.addAttachment(plugin, "essentials.feed", true);
        }

        // Joueur déjà venu par le passé mais qui n'a toujours pas d'île (ex: elle a
        // été supprimée par un admin, ou il n'a jamais fait /ob start) : pas de
        // cinématique dans ce cas (réservée aux tout premiers joins), juste un rappel.
        if (island == null) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&cTu n'as pas encore d'île ! Utilise &f/ob start &cpour en créer une."));
        }
    }
}
