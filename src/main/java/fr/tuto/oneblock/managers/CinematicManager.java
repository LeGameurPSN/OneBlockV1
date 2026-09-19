package fr.tuto.oneblock.managers;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

/**
 * Cinématique jouée à la toute première connexion d'un joueur : une suite de
 * titres/sous-titres + messages de chat qui l'emmène visiter les points clés
 * du spawn (centre, zone AFK, donjon...), avant de lui créer son île
 * OneBlock et de l'y téléporter.
 * <p>
 * Rejouable en test (sans jamais recréer/écraser une île existante) via
 * /cinetest, voir {@link fr.tuto.oneblock.commands.CineTestCommand}.
 */
public class CinematicManager {

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public CinematicManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("intro-cinematic.enabled", true);
    }

    /**
     * Joue la cinématique pour ce joueur.
     *
     * @param testMode si true (appel via /cinetest), la scène finale ne crée
     *                 jamais d'île si le joueur en a déjà une : elle se
     *                 contente de le téléporter dessus, pour ne jamais
     *                 écraser une île existante juste pour un test.
     */
    public void playIntro(Player player, boolean testMode) {
        String worldName = plugin.getConfig().getString("server-spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Cinématique d'arrivée annulée pour " + player.getName()
                    + " : monde \"" + worldName + "\" introuvable. Île créée directement.");
            finishAndSendToIsland(player);
            return;
        }

        long tick = 0L;

        // Mode spectateur pendant toute la cinématique : le joueur peut bouger sa
        // caméra pour regarder autour de lui, mais pas déplacer son corps (pas de
        // marche, pas de chute, pas de dégâts) tant que les téléports s'enchaînent.
        player.setGameMode(GameMode.SPECTATOR);

        // Le mode spectateur permet le vol libre (noclip) : sans ceci, le joueur
        // peut s'éloigner de chaque halte avec les touches de déplacement. On
        // verrouille donc sa POSITION sur celle de la halte en cours à chaque
        // tick, tout en le laissant tourner la caméra librement (yaw/pitch non
        // touchés). Le verrou est retiré juste avant la téléportation finale
        // sur l'île.
        Location[] lockedLocation = new Location[]{null};
        org.bukkit.scheduler.BukkitTask[] lockTask = new org.bukkit.scheduler.BukkitTask[1];
        lockTask[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) return;
            Location target = lockedLocation[0];
            if (target == null) return;
            Location current = player.getLocation();
            double dx = current.getX() - target.getX();
            double dy = current.getY() - target.getY();
            double dz = current.getZ() - target.getZ();
            if ((dx * dx + dy * dy + dz * dz) > 0.01) {
                Location fixed = target.clone();
                fixed.setYaw(current.getYaw());
                fixed.setPitch(current.getPitch());
                player.teleport(fixed);
            }
        }, 1L, 1L);

        // ---- Étape 1 : centre du spawn ----
        Location step1Loc = locationFromConfig(world, "intro-cinematic.step1");
        double step1Duration = plugin.getConfig().getDouble("intro-cinematic.step1.duration-seconds", 5);
        runAt(tick, () -> {
            if (!player.isOnline()) return;
            player.teleport(step1Loc);
            lockedLocation[0] = step1Loc;
            showStep(player, "intro-cinematic.step1", step1Duration);
        });
        tick += secondsToTicks(step1Duration);

        // ---- Étape 2 : zone AFK ----
        Location step2Loc = locationFromConfig(world, "intro-cinematic.step2");
        double step2Duration = plugin.getConfig().getDouble("intro-cinematic.step2.duration-seconds", 5);
        runAt(tick, () -> {
            if (!player.isOnline()) return;
            player.teleport(step2Loc);
            lockedLocation[0] = step2Loc;
            showStep(player, "intro-cinematic.step2", step2Duration);
        });
        tick += secondsToTicks(step2Duration);

        // ---- Étape 3 : donjon ----
        Location step3Loc = locationFromConfig(world, "intro-cinematic.step3");
        double step3Duration = plugin.getConfig().getDouble("intro-cinematic.step3.duration-seconds", 12.5);
        runAt(tick, () -> {
            if (!player.isOnline()) return;
            player.teleport(step3Loc);
            lockedLocation[0] = step3Loc;
            showStep(player, "intro-cinematic.step3", step3Duration);
        });
        tick += secondsToTicks(step3Duration);

        // ---- Étape finale : retour au milieu du spawn, création de l'île en fond ----
        Location spawnCenter = plugin.getManager().getServerSpawnLocation();
        Location finalLoc = spawnCenter != null ? spawnCenter : step1Loc;
        double finalDuration = plugin.getConfig().getDouble("intro-cinematic.final.duration-seconds", 5);
        runAt(tick, () -> {
            if (!player.isOnline()) return;
            player.teleport(finalLoc);
            lockedLocation[0] = finalLoc;
            showStep(player, "intro-cinematic.final", finalDuration);
        });
        tick += secondsToTicks(finalDuration);

        // ---- Fin : création (si besoin) + téléportation sur l'île ----
        runAt(tick, () -> {
            if (lockTask[0] != null) {
                lockTask[0].cancel();
            }
            if (!player.isOnline()) return;
            if (testMode) {
                finishAndSendToIslandTestMode(player);
            } else {
                finishAndSendToIsland(player);
            }
        });
    }

    /** Crée l'île du joueur (il n'en a jamais eu) puis le téléporte dessus. Cas nominal (première connexion). */
    private void finishAndSendToIsland(Player player) {
        OneBlockManager manager = plugin.getManager();
        UUID uuid = player.getUniqueId();
        IslandData island = manager.hasIsland(uuid) ? manager.getIsland(uuid) : manager.createIsland(uuid, player.getName());
        player.setGameMode(GameMode.SURVIVAL);
        player.sendMessage(legacy.deserialize("&a✔ Ton île OneBlock a été créée ! Casse le bloc sous tes pieds pour commencer."));
        player.sendMessage(legacy.deserialize("&7Astuce : &f/chelp &7affiche toutes les commandes essentielles."));
        teleportOnIslandTop(player, island);
    }

    /** Variante /cinetest : ne crée JAMAIS d'île si une existe déjà, se contente d'y téléporter. */
    private void finishAndSendToIslandTestMode(Player player) {
        OneBlockManager manager = plugin.getManager();
        UUID uuid = player.getUniqueId();
        IslandData island;
        if (manager.hasIsland(uuid)) {
            island = manager.getIsland(uuid);
            player.sendMessage(legacy.deserialize("&7[Test] Fin de la cinématique : téléportation sur ton île existante."));
        } else {
            island = manager.createIsland(uuid, player.getName());
            player.sendMessage(legacy.deserialize("&7[Test] Fin de la cinématique : île créée (tu n'en avais pas encore)."));
        }
        player.setGameMode(GameMode.SURVIVAL);
        teleportOnIslandTop(player, island);
    }

    /** Reproduit le comportement de OBCommand#teleportOnTop (bordure, horloge, vol, bossbar). */
    private void teleportOnIslandTop(Player player, IslandData island) {
        OneBlockManager manager = plugin.getManager();
        Location loc = island.getBlockLocation().clone().add(0.5, 1.0, 0.5);
        player.teleport(loc);

        manager.applyBorder(player, island);
        manager.applyTimeLock(player, island);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                manager.applyBorder(player, island);
                manager.applyTimeLock(player, island);
            }
        });

        boolean fly = island.getSetting("fly", false);
        player.setAllowFlight(fly);
        if (!fly && player.isFlying()) {
            player.setFlying(false);
        }

        plugin.getBossBarManager().update(player, island);
    }

    private void showStep(Player player, String path, double durationSeconds) {
        String titleText = plugin.getConfig().getString(path + ".title", "");
        String subtitleText = plugin.getConfig().getString(path + ".subtitle", "");
        String chatText = plugin.getConfig().getString(path + ".chat", "");

        Component title = legacy.deserialize(titleText);
        Component subtitle = legacy.deserialize(subtitleText);
        long totalMillis = secondsToTicks(durationSeconds) * 50L;
        long stayMillis = Math.max(0L, totalMillis - 1000L); // fade-in 0.5s + fade-out 0.5s inclus dans la durée totale
        player.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(stayMillis), Duration.ofMillis(500))));

        if (!chatText.isEmpty()) {
            player.sendMessage(legacy.deserialize(chatText));
        }
    }

    private Location locationFromConfig(World world, String path) {
        double x = plugin.getConfig().getDouble(path + ".x", 0);
        double y = plugin.getConfig().getDouble(path + ".y", 70);
        double z = plugin.getConfig().getDouble(path + ".z", 0);
        float yaw = (float) plugin.getConfig().getDouble(path + ".yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", 0);
        return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
    }

    private long secondsToTicks(double seconds) {
        return Math.round(seconds * 20.0);
    }

    private void runAt(long tick, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(0L, tick));
    }
}
