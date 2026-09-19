package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Applique les réglages de chaque île (équivalent de gamerules, mais limités
 * à la zone de l'île concernée au lieu du monde entier).
 */
public class IslandSettingsListener implements Listener {

    private final OneBlockManager manager;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public IslandSettingsListener(OneBlockPlugin plugin) {
        this.manager = plugin.getManager();
    }

    private boolean setting(IslandData island, org.bukkit.World world, String key, boolean def) {
        return island.getSetting(world, key, def);
    }

    private boolean isOneBlockLocation(Location loc, IslandData island) {
        Location b = island.getBlockLocation();
        return loc.getWorld().equals(b.getWorld())
                && loc.getBlockX() == b.getBlockX()
                && loc.getBlockY() == b.getBlockY()
                && loc.getBlockZ() == b.getBlockZ();
    }

    /** Équivalent de isOneBlockLocation, mais pour le bloc OneBlock du Nether de l'île. */
    private boolean isOneBlockNetherLocation(Location loc, IslandData island) {
        Location b = island.getNetherBlockLocation();
        if (b == null || b.getWorld() == null) return false;
        return loc.getWorld().equals(b.getWorld())
                && loc.getBlockX() == b.getBlockX()
                && loc.getBlockY() == b.getBlockY()
                && loc.getBlockZ() == b.getBlockZ();
    }

    /** Équivalent de isOneBlockLocation, mais pour le bloc OneBlock de l'End de l'île. */
    private boolean isOneBlockEndLocation(Location loc, IslandData island) {
        Location b = island.getEndBlockLocation();
        if (b == null || b.getWorld() == null) return false;
        return loc.getWorld().equals(b.getWorld())
                && loc.getBlockX() == b.getBlockX()
                && loc.getBlockY() == b.getBlockY()
                && loc.getBlockZ() == b.getBlockZ();
    }

    /**
     * true si ce bloc fait partie d'un portail (le portail lui-même, ou un
     * bloc d'obsidienne directement adjacent qui en forme le cadre). Utilisé
     * pour empêcher de casser le portail Nether/End auto-généré par Bukkit,
     * qui ne se reconstruit jamais tout seul contrairement au bloc OneBlock.
     */
    private boolean isPortalFrameBlock(Block block) {
        if (block.getType() == Material.NETHER_PORTAL || block.getType() == Material.END_PORTAL) return true;
        if (block.getType() != Material.OBSIDIAN) return false;
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN}) {
            Material neighbor = block.getRelative(face).getType();
            if (neighbor == Material.NETHER_PORTAL || neighbor == Material.END_PORTAL) return true;
        }
        return false;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        IslandData island = manager.findIslandAt(event.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getLocation()).getWorld();

        Entity entity = event.getEntity();
        if (entity instanceof Monster) {
            // Mobs hostiles : le joueur peut les désactiver via le menu réglages.
            if (!setting(island, __w, "mob-spawning", true)) event.setCancelled(true);
        }
        // Mobs passifs (Animals : vache, mouton, poulet, etc.) : ils sont
        // liés au biome de l'île et doivent TOUJOURS pouvoir spawner. Ce
        // n'est plus un réglage désactivable par le joueur (retiré du menu
        // dans SettingsGUI) : on ne vérifie donc plus aucun setting ici,
        // même pour une île dont la base de données garderait une ancienne
        // valeur "animal-spawning=false".
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Entity damagerEntity = event.getDamager();

        Player attacker = null;
        Monster monsterAttacker = null;
        if (damagerEntity instanceof Player p) {
            attacker = p;
        } else if (damagerEntity instanceof Monster m) {
            monsterAttacker = m;
        } else if (damagerEntity instanceof org.bukkit.entity.Projectile proj) {
            if (proj.getShooter() instanceof Player p) attacker = p;
            else if (proj.getShooter() instanceof Monster m) monsterAttacker = m;
        }

        IslandData island = manager.findIslandAt(victim.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (victim.getLocation()).getWorld();

        if (attacker != null && (!setting(island, __w, "pvp", true) || !island.hasPermission(attacker.getUniqueId(), "pvp"))) {
            event.setCancelled(true);
        } else if (monsterAttacker != null && !setting(island, __w, "monster-damage", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        IslandData island = manager.findIslandAt(player.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (player.getLocation()).getWorld();

        EntityDamageEvent.DamageCause cause = event.getCause();
        switch (cause) {
            case FALL -> {
                if (!setting(island, __w, "fall-damage", true)) event.setCancelled(true);
            }
            case DROWNING -> {
                if (!setting(island, __w, "drown-damage", true)) event.setCancelled(true);
            }
            case VOID -> {
                if (!setting(island, __w, "void-damage", true)) event.setCancelled(true);
            }
            case SUFFOCATION -> {
                if (!setting(island, __w, "suffocation-damage", true)) event.setCancelled(true);
            }
            case FREEZE -> {
                if (!setting(island, __w, "freeze-damage", true)) event.setCancelled(true);
            }
            case CONTACT -> {
                if (!setting(island, __w, "cactus-damage", true)) event.setCancelled(true);
            }
            case LIGHTNING -> {
                if (!setting(island, __w, "lightning-damage", true)) event.setCancelled(true);
            }
            case FIRE, FIRE_TICK, LAVA -> {
                if (!setting(island, __w, "fire-damage", true)) event.setCancelled(true);
            }
            default -> {
                // autres causes non gérées par un réglage dédié
            }
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        IslandData island = manager.findIslandAt(event.getBlock().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getBlock().getLocation()).getWorld();
        if (!setting(island, __w, "fire-spread", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        IslandData island = manager.findIslandAt(event.getBlock().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getBlock().getLocation()).getWorld();
        if (!setting(island, __w, "fire-spread", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        IslandData island = manager.findIslandAt(event.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getLocation()).getWorld();
        if (!setting(island, __w, "explosions", true)) {
            event.blockList().clear();
            return;
        }
        // Même quand les explosions sont autorisées, le portail (auto-généré,
        // non régénérable) ne doit jamais pouvoir sauter avec le reste.
        event.blockList().removeIf(this::isPortalFrameBlock);
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // Endermen qui prennent un bloc, sanglier qui piétine des cultures, etc.
        if (event.getEntity() instanceof Player) return;
        IslandData island = manager.findIslandAt(event.getBlock().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getBlock().getLocation()).getWorld();
        if (!setting(island, __w, "mob-griefing", true)) {
            event.setCancelled(true);
        }
    }

    /**
     * Les feuilles se décomposent normalement (comportement vanilla) pour
     * les arbres classiques. Seule exception : le bloc OneBlock lui-même
     * (overworld ou Nether) ne doit jamais disparaître par décomposition,
     * même si son matériau actuel est un type de feuille.
     */
    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        // La décomposition naturelle des feuilles reste active (comportement
        // vanilla) pour les arbres normaux : on ne l'empêche QUE si la
        // feuille qui se décompose se trouve pile à l'emplacement du bloc
        // OneBlock lui-même (overworld ou Nether), pour ne jamais perdre le
        // bloc OneBlock en cours si son matériau actuel est un type de
        // feuille.
        Location loc = event.getBlock().getLocation();
        IslandData island = manager.findIslandAt(loc);
        if (island == null) return;
        if (isOneBlockLocation(loc, island) || isOneBlockNetherLocation(loc, island)) {
            event.setCancelled(true);
        }
    }

    /** Empêche un piston de pousser un bloc du cadre du portail (le désolidariserait). */
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (isPortalFrameBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Empêche un piston rétractable (à tête collante) d'arracher un bloc du cadre du portail. */
    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (isPortalFrameBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onCropTrample(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.FARMLAND) return;
        if (event.getPlayer().isOp()) return;

        IslandData island = manager.findIslandAt(event.getClickedBlock().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getClickedBlock().getLocation()).getWorld();
        if (!setting(island, __w, "crop-trample", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        IslandData island = manager.findIslandAt(player.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (player.getLocation()).getWorld();
        if (!setting(island, __w, "hunger-loss", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;
        if (!(event.getEntity() instanceof Player player)) return;
        IslandData island = manager.findIslandAt(player.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (player.getLocation()).getWorld();
        if (!setting(island, __w, "natural-regeneration", true)) {
            event.setCancelled(true);
        }
    }

    /**
     * Ramassage des objets au sol : géré UNIQUEMENT par la permission de
     * rôle "item_pickup" (visiteur/trust/membre, voir IslandPermission et
     * /ob permissions). Il n'y a plus de réglage d'île global "item-pickup"
     * qui s'appliquait à tout le monde sans distinction : le propriétaire
     * règle finement qui peut ramasser via /ob permissions.
     */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isOp()) return;
        IslandData island = manager.findIslandAt(player.getLocation());
        if (island == null) return;

        if (!island.hasPermission(player.getUniqueId(), "item_pickup")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        IslandData island = manager.findIslandAt(player.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (player.getLocation()).getWorld();
        // "keep-inventory" n'est PAS modifiable par les joueurs : on lit
        // uniquement la valeur globale fixée dans config.yml, jamais un
        // éventuel réglage stocké sur l'île.
        // EXCEPTION : dans le Nether de l'île, le keep-inventory est
        // TOUJOURS actif, quelle que soit la valeur globale de
        // config.yml (impossible à désactiver dans le Nether).
        boolean keepInventory = island.isNetherWorld(__w)
                || manager.getDefaultSettings().getOrDefault("keep-inventory", false);
        if (keepInventory) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        IslandData island = manager.findIslandAt(event.getEntity().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getEntity().getLocation()).getWorld();

        if (!setting(island, __w, "mob-drops", true)) {
            event.getDrops().clear();
        }
        if (!setting(island, __w, "xp-drop", true)) {
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        IslandData island = manager.findIslandAt(event.getEntity().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getEntity().getLocation()).getWorld();
        if (!setting(island, __w, "potion-splash", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractVillager)) return;
        if (event.getPlayer().isOp()) return;
        IslandData island = manager.findIslandAt(event.getRightClicked().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getRightClicked().getLocation()).getWorld();
        if (!setting(island, __w, "villager-trading", true) || !island.hasPermission(event.getPlayer().getUniqueId(), "villager_trade")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        IslandData island = manager.findIslandAt(event.getBlockClicked().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getBlockClicked().getLocation()).getWorld();

        // Vider un seau (poser de l'eau/lave/autre) revient à poser un bloc :
        // un visiteur (ni propriétaire, ni coéquipier autorisé "build") ne peut
        // jamais le faire sur l'île d'un autre, quel que soit le réglage
        // "bucket-use" de l'île. AVANT ce correctif, seul le réglage
        // "bucket-use" (activé par défaut) était vérifié : n'importe quel
        // visiteur pouvait donc déverser de l'eau/lave n'importe où sur
        // l'île, sans jamais être membre.
        if (!island.canBuild(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'utiliser un seau sur cette île."));
            return;
        }
        if (!setting(island, __w, "bucket-use", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        IslandData island = manager.findIslandAt(event.getBlockClicked().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getBlockClicked().getLocation()).getWorld();

        // Remplir un seau retire un bloc du monde (eau/lave/poudreuse...) :
        // même logique que onBucketEmpty ci-dessus, mais côté "break".
        if (!island.canBreak(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'utiliser un seau sur cette île."));
            return;
        }
        if (!setting(island, __w, "bucket-use", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBuild(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        // Les ops sont totalement libres, comme le propriétaire de n'importe
        // quelle île : ils passent au-dessus de TOUTES les protections de
        // construction, sur toutes les îles sans exception.
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(event.getBlock().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getBlock().getLocation()).getWorld();

        // Un visiteur (ni propriétaire, ni coéquipier autorisé "build") ne peut jamais
        // poser de bloc sur l'île d'un autre, quel que soit le réglage "build" de l'île.
        if (!island.canBuild(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de construire sur cette île."));
            return;
        }
        if (!setting(island, __w, "build", true)) {
            event.setCancelled(true);
        }
    }

    /**
     * Priorité LOWEST (et non plus HIGHEST) : SÉCURITÉ ANTI-DUPLICATION.
     * Avant ce correctif, ce handler tournait en HIGHEST, donc APRÈS tous
     * les plugins tiers en priorité par défaut NORMAL (ex: EconomyShopGUI /
     * un plugin de silk-touch-spawner). Résultat : un visiteur sans la
     * permission "break" pouvait miner un spawner silk touch posé ailleurs
     * sur l'île d'un autre joueur, se faire donner l'objet par le plugin de
     * shop (message "Shop >> You successfully mined a ... Spawner") AVANT
     * que ce handler n'annule l'event et n'affiche le refus de permission.
     * En LOWEST, l'annulation a lieu en tout premier, avant que le moindre
     * autre plugin (respectant l'annulation via ignoreCancelled = true, la
     * norme pour ce type de plugin) ne puisse traiter/donner l'objet.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onBreakOther(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // Les ops sont totalement libres, comme le propriétaire : ils passent
        // au-dessus de TOUTES les protections de casse, sur toutes les îles.
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(event.getBlock().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getBlock().getLocation()).getWorld();
        // Le bloc OneBlock lui-même (overworld, Nether ou End) est géré par
        // BlockBreakListener, pas ici.
        if (isOneBlockLocation(event.getBlock().getLocation(), island)
                || isOneBlockNetherLocation(event.getBlock().getLocation(), island)
                || isOneBlockEndLocation(event.getBlock().getLocation(), island)) return;

        // Le portail (Nether/End) généré automatiquement par Bukkit ne doit
        // JAMAIS pouvoir être cassé, même par le propriétaire/un coéquipier
        // autorisé à casser : contrairement au bloc OneBlock, ce portail ne
        // se régénère PAS tout seul s'il disparaît (voir NetherBlockListener),
        // donc le casser couperait DÉFINITIVEMENT l'accès à cette dimension
        // pour toute l'île.
        if (isPortalFrameBlock(event.getBlock())) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cImpossible de casser le portail : il ne se régénère pas tout seul."));
            return;
        }

        // Un visiteur (ni propriétaire, ni coéquipier autorisé "break") ne peut jamais
        // casser de bloc sur l'île d'un autre, quel que soit le réglage de l'île.
        if (!island.canBreak(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de casser des blocs sur cette île."));
            return;
        }
        if (!setting(island, __w, "block-break-other", true)) {
            event.setCancelled(true);
        }
    }

    /**
     * Frapper un mob (animal ou monstre, pas un joueur — voir onPvp
     * ci-dessus pour le PvP) appartenant à l'île d'un autre : gouverné par
     * la permission de rôle "mob_attack". Empêche un visiteur de venir
     * taper/tuer les animaux ou monstres d'un autre joueur (farm de mobs,
     * élevage, boss custom, etc.) sans y être autorisé.
     */
    @EventHandler
    public void onMobAttack(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) return; // PvP géré par onPvp ci-dessus
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity)) return;

        Entity damagerEntity = event.getDamager();
        Player attacker = null;
        if (damagerEntity instanceof Player p) {
            attacker = p;
        } else if (damagerEntity instanceof org.bukkit.entity.Projectile proj) {
            if (proj.getShooter() instanceof Player p) attacker = p;
        }
        if (attacker == null) return;
        if (attacker.isOp()) return;

        IslandData island = manager.findIslandAt(event.getEntity().getLocation());
        if (island == null) return;

        if (!island.hasPermission(attacker.getUniqueId(), "mob_attack")) {
            event.setCancelled(true);
            attacker.sendMessage(legacy.deserialize("&cTu n'as pas la permission de frapper les mobs de cette île."));
        }
    }

    /**
     * Jeter un objet au sol (touche Q) sur l'île d'un autre : gouverné par
     * la permission de rôle "item_drop". Empêche un visiteur de laisser
     * traîner/planquer des objets sur l'île d'un autre sans y être autorisé
     * (contournement possible de item_pickup sinon : un visiteur pourrait
     * "cacher" un item à lui sur l'île pour le récupérer plus tard, ou
     * gêner le propriétaire en jonchant le sol d'objets).
     */
    @EventHandler
    public void onItemDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(player.getLocation());
        if (island == null) return;

        if (!island.hasPermission(player.getUniqueId(), "item_drop")) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de jeter des objets sur cette île."));
        }
    }

    @EventHandler
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // Les ops peuvent ouvrir n'importe quel coffre/conteneur sur n'importe
        // quelle île, comme le propriétaire.
        if (player.isOp()) return;

        Location loc = event.getInventory().getLocation();
        if (loc == null) return; // inventaire sans bloc (ex: GUI internes du plugin, inventaire joueur)

        IslandData island = manager.findIslandAt(loc);
        if (island == null) return;
        org.bukkit.World __w = (loc).getWorld();

        // Coffre enderien et entonnoirs ont leur propre permission de rôle,
        // plus précise que "containers" (chests/barils/fours/etc.).
        Material invType = event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST
                ? Material.ENDER_CHEST
                : (loc.getBlock().getType() == Material.HOPPER ? Material.HOPPER : null);
        String permKey = invType == Material.ENDER_CHEST ? "ender_chest"
                : invType == Material.HOPPER ? "hoppers"
                : "containers";

        if (!island.hasPermission(player.getUniqueId(), permKey)) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'ouvrir les coffres/conteneurs de cette île."));
        }
    }

    /**
     * Empêche un visiteur d'écrire/modifier le texte d'un panneau (sign) sur
     * l'île d'un autre. AVANT ce correctif, ce cas n'était protégé par AUCUN
     * listener : hors placement (déjà bloqué par onBuild), Minecraft permet
     * à N'IMPORTE QUEL joueur de rouvrir l'éditeur d'un panneau déjà posé en
     * cliquant dessus (clic droit) et d'en changer le texte, sans passer par
     * BlockPlaceEvent. Un visiteur pouvait donc réécrire librement un
     * panneau (ex: panneau de commandes/menu) sans jamais être membre.
     */
    @EventHandler
    public void onSignEdit(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(event.getBlock().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getBlock().getLocation()).getWorld();

        if (!island.canBuild(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de modifier les panneaux de cette île."));
        }
    }

    /**
     * Blocs "interactifs" (portes, trappes, boutons, leviers, redstone,
     * blocs de note, lutrins, cloches, gâteaux, ateliers...) : chacun est
     * rattaché à UNE permission précise du système de rôles (/ob perms, voir
     * IslandPermission), plutôt qu'à un seul "interact" générique. Un
     * visiteur peut donc, par exemple, être autorisé à utiliser une table de
     * craft sans pour autant pouvoir ouvrir une porte, selon les réglages du
     * propriétaire.
     */
    private static final Map<Material, String> PROTECTED_INTERACTABLES = new java.util.HashMap<>();
    static {
        for (Material m : Material.values()) {
            String name = m.name();
            if (!m.isBlock()) continue;
            String key = null;
            if (name.endsWith("_DOOR")) key = "doors";
            else if (name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE")) key = "trapdoors_gates";
            else if (name.endsWith("_BUTTON")) key = "buttons";
            else if (name.equals("LEVER")) key = "levers";
            else if (name.endsWith("_PRESSURE_PLATE")) key = "pressure_plates";
            else if (name.equals("REPEATER") || name.equals("COMPARATOR") || name.equals("DAYLIGHT_DETECTOR"))
                key = "redstone_components";
            else if (name.equals("NOTE_BLOCK") || name.equals("JUKEBOX")) key = "note_jukebox";
            else if (name.endsWith("_BED")) key = "beds";
            else if (name.equals("BELL")) key = "bell";
            else if (name.equals("LECTERN")) key = "lectern";
            else if (name.equals("CAKE") || name.equals("COMPOSTER")) key = "cake_composter";
            else if (name.equals("FLOWER_POT")) key = "flower_pots";
            else if (name.equals("CRAFTING_TABLE")) key = "crafting_table";
            else if (name.equals("ANVIL") || name.equals("CHIPPED_ANVIL") || name.equals("DAMAGED_ANVIL")) key = "anvil";
            else if (name.equals("ENCHANTING_TABLE")) key = "enchanting_table";
            else if (name.equals("BREWING_STAND")) key = "brewing_stand";
            else if (name.equals("GRINDSTONE")) key = "grindstone";
            else if (name.equals("LOOM")) key = "loom";
            else if (name.equals("STONECUTTER")) key = "stonecutter";
            else if (name.equals("CARTOGRAPHY_TABLE")) key = "cartography_table";
            else if (name.equals("SMITHING_TABLE")) key = "smithing_table";

            if (key != null) {
                PROTECTED_INTERACTABLES.put(m, key);
            }
        }
    }

    @EventHandler
    public void onInteractProtectedBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        String permKey = PROTECTED_INTERACTABLES.get(clicked.getType());
        if (permKey == null) return;

        Player player = event.getPlayer();
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(clicked.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (clicked.getLocation()).getWorld();

        if (!island.hasPermission(player.getUniqueId(), permKey)) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'utiliser ce bloc sur cette île."));
        }
    }

    /**
     * Cadres (item frames) et tableaux : un visiteur ne doit ni pouvoir les
     * casser (retire/éjecte l'objet à l'intérieur), ni les faire pivoter.
     * AVANT ce correctif, aucun listener ne protégeait les Hanging (cadres,
     * tableaux) : un visiteur pouvait librement vider/casser un cadre sur
     * l'île d'un autre.
     */
    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player)) return;
        if (player.isOp()) return;

        Hanging hanging = event.getEntity();
        IslandData island = manager.findIslandAt(hanging.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (hanging.getLocation()).getWorld();

        if (!island.canBreak(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de casser ça sur cette île."));
        }
    }

    /**
     * Pivoter l'objet contenu dans un cadre, ou en retirer l'objet par un
     * simple clic droit, ne passe pas par HangingBreakByEntityEvent mais
     * par PlayerInteractEntityEvent (déjà utilisé ci-dessous pour les
     * villageois) : on y ajoute donc aussi la protection des cadres et des
     * porte-armures, chacun via sa propre permission de rôle.
     */
    @EventHandler
    public void onInteractHangingOrStand(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if (!(target instanceof Hanging) && !(target instanceof ArmorStand)) return;

        Player player = event.getPlayer();
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(target.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (target.getLocation()).getWorld();

        String permKey = target instanceof ArmorStand ? "armor_stands" : "item_frames";
        if (!island.hasPermission(player.getUniqueId(), permKey)) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'interagir avec ça sur cette île."));
        }
    }

    /**
     * Manipulation d'un porte-armure à la main (ajout/retrait d'équipement,
     * pose) : un événement distinct de PlayerInteractEntityEvent.
     */
    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(event.getRightClicked().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getRightClicked().getLocation()).getWorld();

        if (!island.hasPermission(player.getUniqueId(), "armor_stands")) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'interagir avec ça sur cette île."));
        }
    }

    /** Tondre un mouton : gouverné par la permission de rôle "animals_shear". */
    @EventHandler
    public void onShear(org.bukkit.event.player.PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(event.getEntity().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getEntity().getLocation()).getWorld();

        if (!island.hasPermission(player.getUniqueId(), "animals_shear")) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de tondre les moutons de cette île."));
        }
    }

    /** Monter dans un minecart / une barque : permission de rôle "vehicles". */
    @EventHandler
    public void onVehicleEnter(org.bukkit.event.vehicle.VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) return;
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(event.getVehicle().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getVehicle().getLocation()).getWorld();

        if (!island.hasPermission(player.getUniqueId(), "vehicles")) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'utiliser ce véhicule sur cette île."));
        }
    }

    /** Attacher/décrocher un animal en laisse : permission de rôle "leash". */
    @EventHandler
    public void onLeash(org.bukkit.event.entity.PlayerLeashEntityEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        IslandData island = manager.findIslandAt(event.getEntity().getLocation());
        if (island == null) return;
        org.bukkit.World __w = (event.getEntity().getLocation()).getWorld();

        if (!island.hasPermission(player.getUniqueId(), "leash")) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'attacher les animaux de cette île."));
        }
    }

    /** Pêcher (lancer la ligne) : permission de rôle "fishing". */
    @EventHandler
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (event.getState() != org.bukkit.event.player.PlayerFishEvent.State.FISHING) return;

        IslandData island = manager.findIslandAt(player.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (player.getLocation()).getWorld();

        if (!island.hasPermission(player.getUniqueId(), "fishing")) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de pêcher sur cette île."));
        }
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isOp()) return;
        IslandData island = manager.findIslandAt(player.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (player.getLocation()).getWorld();
        if (!setting(island, __w, "bow-shooting", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isOp()) return;
        IslandData island = manager.findIslandAt(player.getLocation());
        if (island == null) return;
        org.bukkit.World __w = (player.getLocation()).getWorld();
        if (!setting(island, __w, "entity-mount", true)) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêche RÉELLEMENT de franchir la bordure quand le mode
     * "border-particles" est actif. En mode particules, applyBorder()
     * envoie volontairement au joueur une WorldBorder gigantesque
     * (5.9E7, en pratique jamais atteignable) pour masquer le mur
     * bleu/rouge vanilla et le remplacer par des particules -> la vraie
     * WorldBorder (qui bloque nativement le passage) ne protège donc plus
     * du tout la limite réelle de l'île. On reproduit ici manuellement le
     * blocage physique que la WorldBorder vanilla aurait fait : dès que le
     * joueur essaierait de sortir de la zone (bordure définie par
     * island.getBorderSize()), on ramène sa position X/Z tout juste à
     * l'intérieur de la limite (sa hauteur Y et son regard ne sont pas
     * modifiés). En mode vanilla (non-particules), la vraie WorldBorder
     * envoyée s'en charge déjà nativement, donc ce handler ne fait rien.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        Location to = event.getTo();
        if (to == null) return;

        // On utilise le monde plutôt que findIslandAt(to), car ce dernier se
        // base sur island.contains() : une fois le joueur DÉJÀ hors limites
        // (ce qu'on cherche justement à empêcher), il ne retrouverait plus
        // l'île et laisserait passer le mouvement.
        IslandData island = manager.findIslandByWorld(to.getWorld());
        boolean inNether = false;
        if (island == null) {
            island = manager.findIslandByNetherWorld(to.getWorld());
            inNether = island != null;
        }
        if (island == null) return;
        if (!island.getSetting(to.getWorld(), "border-particles", false)) return;

        Location center = inNether ? island.getNetherBlockLocation() : island.getBlockLocation();
        if (center == null) return;
        double half = island.getBorderSize() / 2.0;
        // Petite marge de sécurité (rayon approximatif de la hitbox joueur)
        // pour ne pas le laisser coller exactement sur la limite.
        double margin = 0.3;
        double minX = center.getX() - half + margin;
        double maxX = center.getX() + half - margin;
        double minZ = center.getZ() - half + margin;
        double maxZ = center.getZ() + half - margin;

        double clampedX = Math.min(Math.max(to.getX(), minX), maxX);
        double clampedZ = Math.min(Math.max(to.getZ(), minZ), maxZ);

        if (clampedX != to.getX() || clampedZ != to.getZ()) {
            Location pushedBack = to.clone();
            pushedBack.setX(clampedX);
            pushedBack.setZ(clampedZ);
            event.setTo(pushedBack);
        }
    }
}
