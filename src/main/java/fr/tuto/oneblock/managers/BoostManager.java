package fr.tuto.oneblock.managers;

import fr.tuto.oneblock.OneBlockPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gère les boosts temporaires accordés individuellement à un joueur via
 * {@code /boost give <x2xp|x2item> <joueur>} (voir BoostCommand), réservé à
 * la permission {@code oneblock.staff.boost}.
 * <p>
 * Le joueur ciblé reçoit un item "Cœur de la mer" : le boost n'est PAS actif
 * tant que l'item n'a pas été activé d'un clic droit (voir
 * BoostItemListener), qui consomme l'item et démarre le minuteur ici.
 * <p>
 * Deux boosts indépendants, chacun avec sa propre date d'expiration en
 * mémoire (non persisté sur disque : un redémarrage du serveur annule les
 * boosts en cours, ce qui est acceptable vu leur courte durée) :
 * <ul>
 *     <li>XP : double la progression de l'île à chaque bloc OneBlock cassé
 *     (Overworld/Nether/End — 1 bloc compte pour 2, voir BlockBreakListener)
 *     et multiplie par 2 l'expérience lâchée par les mobs tués (voir MobKillListener)</li>
 *     <li>OBJET : multiplie par 2 les objets obtenus en cassant le bloc OneBlock
 *     et le butin lâché par les mobs (voir BlockBreakListener#giveItem et MobKillListener)</li>
 * </ul>
 * Pendant qu'un boost est actif, un minuteur s'affiche en permanence dans
 * l'action bar du joueur (juste au-dessus de la barre d'expérience/hotbar).
 */
public class BoostManager {

    /** Multiplicateur appliqué tant qu'un boost est actif. */
    public static final int MULTIPLIER = 2;

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    private final Map<UUID, Long> xpBoostExpiry = new HashMap<>();
    private final Map<UUID, Long> itemBoostExpiry = new HashMap<>();

    private BukkitTask actionBarTask;

    public BoostManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /** Démarre le minuteur d'action bar affiché tant qu'un boost est actif. */
    public void start() {
        if (actionBarTask != null) return;
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickActionBars, 20L, 20L);
    }

    public void stop() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
    }

    private void tickActionBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean xp = isXpBoostActive(uuid);
            boolean item = isItemBoostActive(uuid);
            if (!xp && !item) continue;

            StringBuilder text = new StringBuilder();
            if (xp) {
                text.append("&b&l⚡ x2 XP &7(&f").append(formatDuration(getRemainingXpMillis(uuid))).append("&7)");
            }
            if (xp && item) {
                text.append("  &8|  ");
            }
            if (item) {
                text.append("&6&l⛏ x2 Objets &7(&f").append(formatDuration(getRemainingItemMillis(uuid))).append("&7)");
            }
            player.sendActionBar(legacy.deserialize(text.toString()));
        }
    }

    public void startXpBoost(UUID uuid, long durationMillis) {
        xpBoostExpiry.put(uuid, System.currentTimeMillis() + durationMillis);
    }

    public void startItemBoost(UUID uuid, long durationMillis) {
        itemBoostExpiry.put(uuid, System.currentTimeMillis() + durationMillis);
    }

    public boolean isXpBoostActive(UUID uuid) {
        return remainingMillis(xpBoostExpiry, uuid) > 0;
    }

    public boolean isItemBoostActive(UUID uuid) {
        return remainingMillis(itemBoostExpiry, uuid) > 0;
    }

    /** Renvoie {@link #MULTIPLIER} si le boost XP est actif, sinon 1. */
    public int getXpMultiplier(UUID uuid) {
        return isXpBoostActive(uuid) ? MULTIPLIER : 1;
    }

    /** Renvoie {@link #MULTIPLIER} si le boost objet est actif, sinon 1. */
    public int getItemMultiplier(UUID uuid) {
        return isItemBoostActive(uuid) ? MULTIPLIER : 1;
    }

    public long getRemainingXpMillis(UUID uuid) {
        return remainingMillis(xpBoostExpiry, uuid);
    }

    public long getRemainingItemMillis(UUID uuid) {
        return remainingMillis(itemBoostExpiry, uuid);
    }

    private long remainingMillis(Map<UUID, Long> map, UUID uuid) {
        Long expiry = map.get(uuid);
        if (expiry == null) return 0L;
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            map.remove(uuid);
            return 0L;
        }
        return remaining;
    }

    /** Formate une durée en millisecondes en "Xm Ys" pour les messages. */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + "m " + seconds + "s";
    }
}
