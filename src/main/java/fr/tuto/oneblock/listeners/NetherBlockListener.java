package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.UUID;

/**
 * Redirige les portails du Nether et de l'End vers le monde dédié de la
 * dimension correspondante pour l'ÎLE PHYSIQUE d'où part le portail (celle du
 * monde overworld où le joueur se trouve), et non pour l'île du joueur qui
 * l'emprunte : un visiteur ou coéquipier qui prend un portail sur l'île de
 * quelqu'un d'autre est donc envoyé dans LE Nether/End de cette île-là, pas
 * dans le sien. Toute l'équipe (propriétaire, coéquipiers, joueurs de
 * confiance) est redirigée vers le MÊME monde, puisque
 * IslandData#getNetherBlockLocation / getEndBlockLocation sont partagés au
 * niveau de l'île, pas du joueur individuel. Le trajet retour (Nether/End ->
 * Overworld) est symétrique : il ramène sur l'overworld de CETTE île.
 *
 * Le monde Nether/End de l'île est créé (et son premier OneBlock posé) à la
 * toute première entrée d'un membre de l'île dans cette dimension.
 */
public class NetherBlockListener implements Listener {

    private final OneBlockPlugin plugin;

    public NetherBlockListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    // Bukkit crée automatiquement un portail (obsidienne) tout PRÈS de la
    // destination si aucun n'existe déjà dans le monde d'arrivée — ce qui est
    // toujours le cas ici puisque les mondes Nether/End/île sont générés
    // vides. Si la destination est pile sur le bloc OneBlock, ce portail se
    // forme dessus/juste à côté et casser le OneBlock casse le portail avec.
    // On fait donc atterrir le joueur à quelques blocs de distance du
    // OneBlock, pour que le portail se construise là et laisse le OneBlock
    // tranquille.
    private static final int PORTAL_LANDING_OFFSET = 3;

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        World to = event.getTo() != null ? event.getTo().getWorld() : null;
        if (to == null) return;

        OneBlockManager manager = plugin.getManager();
        UUID uuid = player.getUniqueId();
        World from = event.getFrom().getWorld();

        if (to.getEnvironment() == World.Environment.NETHER) {
            // Le portail mène au Nether de l'île du monde OVERWORLD d'où il
            // part (celle du propriétaire dont c'est physiquement l'île), pas
            // à celle du joueur qui l'emprunte : un visiteur/coéquipier qui
            // prend un portail sur l'île de quelqu'un d'autre est donc
            // envoyé dans LE Nether de cette île-là, pas dans le sien.
            IslandData island = manager.findIslandByOverworld(from);
            if (island == null) {
                // Repli : portail pris hors de toute île connue (ex: monde
                // "world"/spawn) -> comportement précédent, basé sur l'île du joueur.
                island = manager.getIsland(uuid);
            }
            if (island == null) {
                event.setCancelled(true);
                player.sendMessage("§cTu dois avoir une île OneBlock (/ob start) pour accéder au Nether.");
                return;
            }
            String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
            String worldName = manager.getNetherWorldName(island.getOwner(), ownerName);
            World netherWorld = manager.getOrCreateNetherWorld(worldName);
            manager.initNetherIslandIfAbsent(island, netherWorld);

            Location dest = island.getNetherBlockLocation().clone()
                    .add(0.5 + PORTAL_LANDING_OFFSET, 1.0, 0.5);
            event.setTo(dest);
            // La bordure est réappliquée dans onWorldChange ci-dessous, une
            // fois que le joueur est RÉELLEMENT dans le nouveau monde (voir
            // ce handler pour l'explication du bug corrigé ici).
            return;
        }

        if (to.getEnvironment() == World.Environment.THE_END) {
            IslandData island = manager.findIslandByOverworld(from);
            if (island == null) {
                island = manager.getIsland(uuid);
            }
            if (island == null) {
                event.setCancelled(true);
                player.sendMessage("§cTu dois avoir une île OneBlock (/ob start) pour accéder à l'End.");
                return;
            }
            String ownerName = Bukkit.getOfflinePlayer(island.getOwner()).getName();
            String worldName = manager.getEndWorldName(island.getOwner(), ownerName);
            World endWorld = manager.getOrCreateEndWorld(worldName);
            manager.initEndIslandIfAbsent(island, endWorld);

            Location dest = island.getEndBlockLocation().clone()
                    .add(0.5 + PORTAL_LANDING_OFFSET, 1.0, 0.5);
            event.setTo(dest);
            return;
        }

        // Portail retour (Nether -> Overworld, ou End -> Overworld) : sans ce
        // cas, Bukkit utilise la logique de portail vanilla par défaut, qui
        // ne trouve rien dans le monde Nether/End (100% vide) et se contente
        // d'y creuser/poser un NOUVEAU portail en obsidienne sur place, sans
        // jamais ramener le joueur sur son île overworld. On force donc le
        // retour exact vers le bloc OneBlock de l'île (décalé pour la même
        // raison que ci-dessus).
        if (to.getEnvironment() == World.Environment.NORMAL) {
            IslandData island = manager.findIslandByNetherOrEnd(from);
            if (island == null) {
                island = manager.getIsland(uuid);
            }
            if (island != null && island.getBlockLocation() != null
                    && island.getBlockLocation().getWorld() != null) {
                Location dest = island.getBlockLocation().clone()
                        .add(0.5 + PORTAL_LANDING_OFFSET, 1.0, 0.5);
                event.setTo(dest);
                // Bordure réappliquée dans onWorldChange ci-dessous.
            }
        }
    }

    /**
     * Empêche les joueurs de construire eux-mêmes un AUTRE portail (cadre
     * d'obsidienne + briquet) dans le Nether d'une île : un seul portail par
     * île doit exister dans son monde Nether, celui généré automatiquement
     * par Bukkit lors du premier trajet (voir onPortal ci-dessus, qui crée
     * le portail via un PlayerPortalEvent -> raison NETHER_PAIR, jamais
     * FIRE). On ne bloque que la création "manuelle" (raison FIRE, un
     * joueur qui allume un cadre avec un briquet) ; la création automatique
     * du plugin n'est jamais concernée.
     * Ne s'applique qu'au monde Nether dédié d'une île (pas à l'overworld,
     * ni à l'End) : un joueur reste libre de poser/allumer de l'obsidienne
     * sur son île overworld ou dans l'End.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() != PortalCreateEvent.CreateReason.FIRE) return;

        World world = event.getWorld();
        OneBlockManager manager = plugin.getManager();
        if (manager.findIslandByNetherWorld(world) == null) return;

        event.setCancelled(true);
    }

    /**
     * BUG CORRIGÉ : envoyer la nouvelle WorldBorder juste après avoir défini
     * event.setTo(...) (ou même un tick plus tard via le scheduler) ne
     * suffit PAS pour un portail multi-monde : le changement de monde
     * effectif côté serveur (et donc côté client, qui doit d'abord charger
     * les chunks du nouveau monde) peut prendre plus d'un tick, notamment la
     * toute première fois qu'un joueur entre dans le Nether d'une île (monde
     * fraîchement créé). Toute WorldBorder envoyée avant que le client soit
     * effectivement passé dans le nouveau monde est silencieusement ignorée
     * ou écrasée par le paquet de changement de monde qui suit -> la
     * bordure de l'overworld restait affichée (ou aucune bordure du tout)
     * une fois dans le Nether.
     *
     * PlayerChangedWorldEvent se déclenche exactement au bon moment : APRÈS
     * que le joueur soit RÉELLEMENT dans le nouveau monde côté serveur. On y
     * réapplique donc la bordure (et l'heure verrouillée) de façon fiable,
     * quelle que soit la cause du changement de monde (portail Nether/End,
     * /ob tp, /is visit, etc.), plutôt que de multiplier des appels ad hoc
     * après chaque téléportation.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World newWorld = player.getWorld();
        OneBlockManager manager = plugin.getManager();

        IslandData island = manager.findIslandByWorld(newWorld);
        if (island == null) island = manager.findIslandByNetherWorld(newWorld);
        if (island == null) island = manager.findIslandByEndWorld(newWorld);

        if (island == null) {
            // Monde sans île connue (ex: spawn du serveur) : pas de bordure ni de BossBar à afficher.
            player.setWorldBorder(null);
            manager.stopBorderParticles(player);
            plugin.getBossBarManager().remove(player);
            return;
        }

        manager.applyBorder(player, island);
        manager.applyTimeLock(player, island);
        // La BossBar de niveau (voir BossBarManager) n'était mise à jour
        // qu'après avoir cassé un bloc : un joueur qui vient d'arriver dans
        // le Nether (ou dans n'importe quel monde géré) ne la voyait donc
        // pas tant qu'il n'avait pas encore miné une seule fois. On la
        // (ré)affiche systématiquement dès l'arrivée dans le monde.
        plugin.getBossBarManager().update(player, island);
    }
}
