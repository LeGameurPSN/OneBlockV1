package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.BoostManager;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Comptabilise les mobs tués par chaque joueur (pour le classement
 * /top mob), indépendamment de l'île sur laquelle le kill a lieu : le
 * compteur est toujours crédité à l'île du TUEUR, pas à celle du terrain
 * où se trouve le mob (utile si un joueur tue un mob chez un autre).
 */
public class MobKillListener implements Listener {

    private final OneBlockManager manager;
    private final BoostManager boostManager;

    public MobKillListener(OneBlockPlugin plugin) {
        this.manager = plugin.getManager();
        this.boostManager = plugin.getBoostManager();
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        IslandData island = manager.getIsland(killer.getUniqueId());
        if (island == null) return;

        island.incrementMobsKilled();

        // Boost staff "x2 XP" (/boost give x2xp <joueur>, voir BoostManager).
        int xpMultiplier = boostManager.getXpMultiplier(killer.getUniqueId());
        if (xpMultiplier > 1) {
            event.setDroppedExp(event.getDroppedExp() * xpMultiplier);
        }

        // Boost staff "x2 objets" : double le butin vanilla du mob, en
        // respectant la taille de pile max de chaque item (comme pour
        // BlockBreakListener#giveItem, on duplique plutôt que d'augmenter
        // le montant au-delà du raisonnable).
        int itemMultiplier = boostManager.getItemMultiplier(killer.getUniqueId());
        if (itemMultiplier > 1) {
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack drop : event.getDrops()) {
                for (int i = 1; i < itemMultiplier; i++) {
                    extra.add(drop.clone());
                }
            }
            event.getDrops().addAll(extra);
        }
    }
}
