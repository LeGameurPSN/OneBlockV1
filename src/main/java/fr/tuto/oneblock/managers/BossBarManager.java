package fr.tuto.oneblock.managers;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.models.IslandData;
import fr.tuto.oneblock.models.OneBlockLevel;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Affiche une BossBar persistante avec le niveau actuel du joueur et sa
 * progression (blocs cassés / blocs nécessaires pour passer au niveau
 * suivant).
 */
public class BossBarManager {

    private final OneBlockPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    private static final BarColor[] LEVEL_COLORS = {
            BarColor.GREEN, BarColor.BLUE, BarColor.YELLOW, BarColor.RED, BarColor.PURPLE, BarColor.PINK
    };

    public BossBarManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void update(Player player, IslandData island) {
        if (!plugin.getConfig().getBoolean("bossbar-enabled", true)) return;

        OneBlockLevel level = plugin.getManager().getLevelForCount(island.getBrokenCount());
        if (level == null) return;

        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
            created.addPlayer(player);
            return created;
        });

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        double progress;
        String progressText;
        if (level.getMaxBlocks() < 0) {
            progress = 1.0;
            progressText = island.getBrokenCount() + " blocs cassés (niveau illimité)";
        } else {
            int span = Math.max(1, level.getMaxBlocks() - level.getMinBlocks() + 1);
            int done = island.getBrokenCount() - level.getMinBlocks();
            progress = Math.max(0.0, Math.min(1.0, (double) done / span));
            progressText = done + "/" + span + " blocs";
        }

        BarColor color = LEVEL_COLORS[(level.getId() - 1) % LEVEL_COLORS.length];
        bar.setColor(color);
        bar.setProgress(progress);
        bar.setTitle("§6§lNiveau " + level.getId() + " §7- §f" + level.getName() + " §8| §e" + progressText);
    }

    public void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }
}
