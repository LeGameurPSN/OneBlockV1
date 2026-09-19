package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Map;

/**
 * Stack automatique des spawners (vendus/récupérés via EconomyShopGUI ou
 * n'importe quelle source donnant un item SPAWNER avec son type de mob déjà
 * défini dans le meta du bloc).
 * <p>
 * Quand un joueur pose un spawner directement à côté (les 6 faces, y compris
 * dessus/dessous) d'un spawner déjà existant DU MÊME TYPE de mob, les deux
 * spawners fusionnent : la pose est annulée, l'item est retiré de la main du
 * joueur, et le spawner déjà en place voit son compteur de stack augmenter de
 * 1 (affiché au-dessus du bloc via un TextDisplay flottant, ex: "Zombie x5").
 * En cassant un spawner stacké, on ne récupère qu'UN SEUL spawner à la fois
 * (le bloc reste en place tant qu'il reste au moins 1 dans le stack) : ça
 * évite d'avoir à reposer un par un tous les spawners fusionnés.
 */
public class SpawnerStackListener implements Listener {

    private final OneBlockPlugin plugin;
    private final NamespacedKey stackKey;
    private final NamespacedKey displayMarkerKey;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    private final boolean enabled;
    private final int maxStack;

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    public SpawnerStackListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.stackKey = new NamespacedKey(plugin, "spawner_stack_amount");
        this.displayMarkerKey = new NamespacedKey(plugin, "spawner_stack_display");
        this.enabled = plugin.getConfig().getBoolean("spawner-stacking.enabled", true);
        this.maxStack = Math.max(1, plugin.getConfig().getInt("spawner-stacking.max-stack", 64));
    }

    /**
     * Pose d'un spawner à côté d'un autre spawner du même type de mob : les
     * deux fusionnent au lieu de poser un second bloc. On tourne en HIGH
     * (donc après IslandSettingsListener#onBuild, en priorité NORMAL, qui
     * gère déjà la permission "build") avec ignoreCancelled = true : si la
     * pose est déjà refusée pour une autre raison, on ne fait rien de plus.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!enabled) return;
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;
        if (!(block.getState() instanceof CreatureSpawner placedState)) return;

        EntityType placedType = placedState.getSpawnedType();
        if (placedType == null) return;

        for (BlockFace face : ADJACENT_FACES) {
            Block neighbor = block.getRelative(face);
            if (neighbor.getType() != Material.SPAWNER) continue;
            if (!(neighbor.getState() instanceof CreatureSpawner neighborState)) continue;
            if (neighborState.getSpawnedType() != placedType) continue;

            int current = neighborState.getPersistentDataContainer()
                    .getOrDefault(stackKey, PersistentDataType.INTEGER, 1);
            if (current >= maxStack) continue; // stack déjà pleine sur cette face, on essaie les autres

            // Fusion : on annule totalement la pose du nouveau bloc.
            event.setCancelled(true);

            Player player = event.getPlayer();
            consumeOneFromHand(player, event.getHand());

            int newCount = current + 1;
            neighborState.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, newCount);
            neighborState.update(true, false);

            updateDisplay(neighbor, placedType, newCount);

            player.sendMessage(legacy.deserialize("&a✔ Spawner fusionné ! Stack actuel : &e" + newCount
                    + "x &a(" + formatEntity(placedType) + ")"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
            return; // une seule fusion par pose, même s'il y a plusieurs voisins compatibles
        }
        // Aucune fusion possible : pose normale, le spawner démarre avec un stack de 1 (pas d'affichage tant qu'il reste seul).
    }

    /**
     * Casse d'un spawner stacké : priorité LOW (donc avant EconomyShopGUI ou
     * tout autre plugin en priorité NORMAL qui donnerait normalement l'item
     * du spawner à la casse), avec ignoreCancelled = true pour respecter la
     * permission "break" déjà vérifiée en LOWEST par IslandSettingsListener.
     * Tant qu'il reste plus d'un spawner dans le stack, le bloc n'est JAMAIS
     * réellement cassé : on décrémente juste le compteur et on donne un seul
     * item spawner au joueur.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled) return;
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;
        if (!(block.getState() instanceof CreatureSpawner state)) return;

        int current = state.getPersistentDataContainer().getOrDefault(stackKey, PersistentDataType.INTEGER, 1);
        if (current <= 1) {
            // Casse normale (dernier spawner du stack, ou spawner jamais stacké) :
            // on laisse faire, on nettoie juste un éventuel affichage résiduel.
            removeDisplay(block);
            return;
        }

        event.setCancelled(true);

        int newCount = current - 1;
        state.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, newCount);
        state.update(true, false);

        Player player = event.getPlayer();
        ItemStack drop = buildSpawnerItem(state.getSpawnedType());
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
        for (ItemStack over : leftover.values()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), over);
        }

        updateDisplay(block, state.getSpawnedType(), newCount);
        player.sendMessage(legacy.deserialize("&a✔ Spawner retiré du stack. Il en reste &e" + newCount + "x&a."));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.6f);
    }

    private void consumeOneFromHand(Player player, EquipmentSlot hand) {
        boolean offHand = hand == EquipmentSlot.OFF_HAND;
        ItemStack held = offHand ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (held.getType() != Material.SPAWNER) return;
        if (held.getAmount() <= 1) {
            // On enlève complètement l'item plutôt que de laisser une pile "fantôme" à 0.
            if (offHand) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private ItemStack buildSpawnerItem(EntityType type) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        if (item.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof CreatureSpawner cs) {
            cs.setSpawnedType(type);
            meta.setBlockState(cs);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Affiche/actualise le compteur flottant "Type x N" au-dessus du spawner (retiré si N <= 1). */
    private void updateDisplay(Block block, EntityType type, int count) {
        removeDisplay(block);
        if (count <= 1) return;

        Location loc = block.getLocation().add(0.5, 1.3, 0.5);
        block.getWorld().spawn(loc, TextDisplay.class, display -> {
            display.text(legacy.deserialize("&b&l" + formatEntity(type) + " &7x&e" + count));
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(true);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.getPersistentDataContainer().set(displayMarkerKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Supprime le(s) TextDisplay flottant(s) éventuellement présent(s) au-dessus de ce spawner. */
    private void removeDisplay(Block block) {
        Location loc = block.getLocation().add(0.5, 1.3, 0.5);
        for (org.bukkit.entity.Entity entity : block.getWorld().getNearbyEntities(loc, 0.6, 0.6, 0.6)) {
            if (entity instanceof TextDisplay display
                    && display.getPersistentDataContainer().has(displayMarkerKey, PersistentDataType.BYTE)) {
                display.remove();
            }
        }
    }

    private String formatEntity(EntityType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
