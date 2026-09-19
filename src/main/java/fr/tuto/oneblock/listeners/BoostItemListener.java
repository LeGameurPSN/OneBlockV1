package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.commands.BoostCommand;
import fr.tuto.oneblock.managers.BoostManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Active le boost (x2 XP ou x2 objets) contenu dans un "Cœur de la mer"
 * lorsqu'un joueur fait un clic droit dessus (voir BoostCommand pour la
 * distribution de l'item et BoostManager pour l'effet réel du boost et son
 * minuteur affiché en action bar). L'item est consommé à l'activation.
 */
public class BoostItemListener implements Listener {

    private final OneBlockPlugin plugin;
    private final BoostManager boostManager;
    private final NamespacedKey boostTypeKey;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    private static final long DURATION_MILLIS = 10L * 60L * 1000L; // 10 minutes

    public BoostItemListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.boostManager = plugin.getBoostManager();
        this.boostTypeKey = new NamespacedKey(plugin, BoostCommand.BOOST_TYPE_KEY);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // On ne traite le clic qu'une seule fois (main hand), pas deux fois
        // (main hand + off hand) comme Bukkit le fait pour certains items.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.HEART_OF_THE_SEA) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(boostTypeKey, PersistentDataType.STRING)) return;

        // Empêche d'ouvrir/interagir avec un bloc visé en même temps que
        // l'activation (ex: coffre), et évite le double-déclenchement.
        event.setCancelled(true);

        String type = meta.getPersistentDataContainer().get(boostTypeKey, PersistentDataType.STRING);
        Player player = event.getPlayer();

        // Consomme un seul exemplaire de l'item, même si le joueur en a plusieurs en stack.
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        String typeLabel;
        if ("x2xp".equals(type)) {
            boostManager.startXpBoost(player.getUniqueId(), DURATION_MILLIS);
            typeLabel = "&b&lx2 XP";
        } else if ("x2item".equals(type)) {
            boostManager.startItemBoost(player.getUniqueId(), DURATION_MILLIS);
            typeLabel = "&6&lx2 Objets";
        } else {
            return; // type inconnu (item corrompu/ancienne version), on ne fait rien de plus
        }

        String durationText = BoostManager.formatDuration(DURATION_MILLIS);
        player.sendMessage(legacy.deserialize("&a✔ Boost " + typeLabel + " &aactivé pour &f" + durationText + "&a !"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.8f);
    }
}
