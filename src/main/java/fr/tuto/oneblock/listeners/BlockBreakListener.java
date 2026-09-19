package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import fr.tuto.oneblock.models.OneBlockLevel;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class BlockBreakListener implements Listener {

    private final OneBlockPlugin plugin;
    private final OneBlockManager manager;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    // Délai minimum entre deux casses valides du bloc OneBlock d'une même
    // île (voir le commentaire détaillé dans onBreak ci-dessous).
    private static final long MIN_BREAK_DELAY_MILLIS = 500L;

    public BlockBreakListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    /**
     * Empêche de MÊME COMMENCER à casser le bloc OneBlock pendant le
     * cooldown (voir MIN_BREAK_DELAY_MILLIS ci-dessus) : contrairement à
     * BlockBreakEvent (qui ne se déclenche qu'une fois le bloc entièrement
     * cassé), BlockDamageEvent se déclenche dès le premier coup. En
     * l'annulant ici, la barre de progression de casse ne démarre même pas
     * tant que le cooldown n'est pas écoulé, au lieu de laisser le joueur
     * miner le bloc en entier pour rien.
     */
    @EventHandler
    public void onDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        IslandData island = manager.findIslandAt(block.getLocation());
        if (island != null && isSameBlock(block.getLocation(), island.getBlockLocation())) {
            // SÉCURITÉ ANTI-DUPLICATION (spawner sur l'île d'un autre) : on
            // empêche ICI-MÊME de commencer à miner le bloc OneBlock si le
            // joueur n'a pas la permission "break" sur CETTE île (ni op, ni
            // propriétaire, ni coéquipier/trust autorisé). Avant ce
            // correctif, seul BlockBreakEvent (onBreak, plus bas) vérifiait
            // la permission : un visiteur non autorisé pouvait quand même
            // lancer/annuler-relancer la casse (autoclicker, paquets de
            // casse envoyés manuellement, dupe client) et, sur certains cas
            // de désync, obtenir quand même la récompense (spawner y
            // compris) de façon répétée = duplication à l'infini. On
            // bloque donc désormais l'action dès le premier coup de pioche,
            // pas seulement à la casse finale.
            if (!player.isOp() && !island.canBreak(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            long now = System.currentTimeMillis();
            if (now - island.getLastBreakMillis() < MIN_BREAK_DELAY_MILLIS) {
                event.setCancelled(true);
            }
            return;
        }

        IslandData netherIsland = findIslandByNetherBlock(block.getLocation());
        if (netherIsland != null) {
            if (!player.isOp() && !netherIsland.canBreak(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            long now = System.currentTimeMillis();
            if (now - netherIsland.getLastBreakMillis() < MIN_BREAK_DELAY_MILLIS) {
                event.setCancelled(true);
            }
            return;
        }

        IslandData endIsland = findIslandByEndBlock(block.getLocation());
        if (endIsland != null) {
            if (!player.isOp() && !endIsland.canBreak(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            long now = System.currentTimeMillis();
            if (now - endIsland.getLastBreakMillis() < MIN_BREAK_DELAY_MILLIS) {
                event.setCancelled(true);
            }
        }
    }

    /** Retrouve l'île dont le OneBlock Nether est à cet emplacement, s'il y en a une. */
    private IslandData findIslandByNetherBlock(Location loc) {
        for (IslandData island : manager.getAllIslands()) {
            Location netherLoc = island.getNetherBlockLocation();
            if (netherLoc != null && isSameBlock(loc, netherLoc)) return island;
        }
        return null;
    }

    /** Retrouve l'île dont le OneBlock End est à cet emplacement, s'il y en a une. */
    private IslandData findIslandByEndBlock(Location loc) {
        for (IslandData island : manager.getAllIslands()) {
            Location endLoc = island.getEndBlockLocation();
            if (endLoc != null && isSameBlock(loc, endLoc)) return island;
        }
        return null;
    }

    /**
     * SÉCURITÉ ANTI-DUPLICATION (root cause EconomyShopGUI / silk touch
     * spawner) : ce handler ne fait QUE vérifier la permission "break" et
     * annuler l'event si besoin, et tourne en priorité LOWEST (la toute
     * première appelée par Bukkit, avant tous les autres plugins qui
     * écoutent BlockBreakEvent en priorité par défaut NORMAL/LOW, comme
     * EconomyShopGUI ou un plugin de silk-touch-spawner).
     *
     * AVANT ce correctif, le reste de la vérification de permission se
     * trouvait uniquement dans onBreak() ci-dessous, qui tourne en priorité
     * NORMAL (par défaut). Or EconomyShopGUI (ou équivalent) traite
     * lui-même le drop du spawner en silk touch AVANT que notre annulation
     * n'ait lieu, car son propre listener s'exécute plus tôt dans l'ordre
     * des priorités Bukkit : un visiteur sans la permission "break" pouvait
     * donc quand même récupérer le spawner (donné par EconomyShopGUI) avant
     * même que la casse soit refusée côté OneBlock -> duplication à
     * l'infini en recasser/repose (protégé par ailleurs, voir onPlace),
     * mais surtout obtention de spawners gratuits sur l'île d'un autre.
     *
     * En annulant l'event ICI, en LOWEST, tout plugin bien écrit qui
     * respecte l'annulation (ignoreCancelled = true, ce qui est la norme
     * pour ce type de plugins) ne traitera plus jamais le drop.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreakPermissionCheck(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        Block broken = event.getBlock();

        IslandData netherIsland = findIslandByNetherBlock(broken.getLocation());
        if (netherIsland != null) {
            if (!netherIsland.canBreak(player.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        IslandData endIsland = findIslandByEndBlock(broken.getLocation());
        if (endIsland != null) {
            if (!endIsland.canBreak(player.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        IslandData island = manager.findIslandAt(broken.getLocation());
        if (island == null) return;
        if (!isSameBlock(broken.getLocation(), island.getBlockLocation())) return;

        if (!island.canBreak(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        // NB : on garde ignoreCancelled = false volontairement, car
        // onBreakPermissionCheck() ci-dessus (LOWEST) peut avoir annulé
        // l'event pour un joueur non autorisé — mais c'est justement CE
        // handler-ci qui gère tout le cycle de casse du bloc OneBlock
        // (régénération, etc.), donc il doit continuer à s'exécuter même
        // quand l'event est déjà annulé. La vérification de permission
        // complète ci-dessous (canBreak) reste la source de vérité pour
        // décider si on continue ou non le traitement.
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Block broken = event.getBlock();

        // ==================== ONEBLOCK NETHER ====================
        IslandData netherIsland = findIslandByNetherBlock(broken.getLocation());
        if (netherIsland != null) {
            handleDimensionBreak(event, player, uuid, netherIsland, true);
            return;
        }

        // ==================== ONEBLOCK END ====================
        IslandData endIsland = findIslandByEndBlock(broken.getLocation());
        if (endIsland != null) {
            handleDimensionBreak(event, player, uuid, endIsland, false);
            return;
        }

        // ==================== ONEBLOCK OVERWORLD (comportement existant) ====================

        // On cherche l'île qui contient l'EMPLACEMENT cassé (et non l'île du joueur),
        // pour bien gérer le cas d'un joueur en visite sur l'île d'un autre.
        IslandData island = manager.findIslandAt(broken.getLocation());
        if (island == null) return;

        Location blockLoc = island.getBlockLocation();

        // On ne s'occupe que du bloc OneBlock de cette île
        if (!isSameBlock(broken.getLocation(), blockLoc)) return;

        // Seuls le propriétaire et ses coéquipiers autorisés ("break") peuvent casser
        // le bloc OneBlock. Un simple visiteur (ex: /ob visit) ne peut jamais le casser.
        // Les ops sont totalement libres, comme le propriétaire, sur toutes les îles.
        if (!player.isOp() && !island.canBreak(uuid)) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de casser le bloc OneBlock de cette île."));
            return;
        }

        // On gère nous-même TOUT le cycle de casse (récompense + régénération),
        // donc on annule l'événement natif. Sinon, une fois nos handlers
        // terminés, Bukkit casse quand même le bloc "pour de vrai" et remet
        // AIR à la place de celui qu'on vient de régénérer -> le bloc ne
        // réapparaissait plus du tout.
        event.setCancelled(true);

        if (island.isRegenerating()) {
            // Le bloc est en cours de régénération, on empêche toute action
            return;
        }

        // SÉCURITÉ ANTI-DUPLICATION : on vérifie que le bloc réellement en
        // place correspond bien au bloc OneBlock attendu (mémorisé dans
        // island.getCurrentMaterial() / getCurrentSpawnerEntity()). Avant ce
        // correctif, seul l'EMPLACEMENT était vérifié : si le vrai bloc
        // OneBlock n'avait pas réapparu (bug de régénération), l'emplacement
        // restait en AIR et un joueur pouvait y poser N'IMPORTE QUEL bloc à
        // la main puis le casser -> le plugin le traitait quand même comme
        // le bloc OneBlock et donnait la récompense + relançait la
        // régénération, permettant de reposer/recasser à l'infini pour
        // farmer des récompenses gratuites ("duplication"). Avec ce
        // correctif combiné à la régénération désormais fiabilisée, un bloc
        // qui ne correspond pas est simplement remplacé de force, sans
        // aucune récompense.
        boolean spawnerExpected = island.getCurrentSpawnerEntity() != null;
        boolean matchesExpectedBlock = spawnerExpected
                ? broken.getType() == Material.SPAWNER
                : broken.getType() == island.getCurrentMaterial();
        if (!matchesExpectedBlock) {
            player.sendMessage(legacy.deserialize("&cCe bloc ne correspond pas au bloc OneBlock attendu, il a été restauré."));
            OneBlockLevel currentLevel = manager.getLevelForCount(island.getBrokenCount());
            manager.regenerateBlock(island, currentLevel);
            return;
        }

        // COOLDOWN ANTI-SPAM : le bloc OneBlock ne peut être cassé qu'une
        // fois toutes les 500ms (0.5s) MINIMUM. Comme tout le cycle de
        // casse (récompense + régénération, cf. plus haut) est exécuté de
        // façon totalement synchrone dans ce handler, deux BlockBreakEvent
        // arrivant trop rapprochés (double-clic très rapide, autoclicker,
        // désync client/serveur qui envoie 2 paquets de casse la même tick)
        // peuvent être traités avant que le client ait eu le temps de
        // "voir" le nouveau bloc posé : le second event se produit alors
        // sur un bloc qui, côté serveur, est déjà en train d'être remplacé,
        // et le bloc OneBlock finit par ne plus réapparaître du tout. On
        // ignore donc silencieusement toute casse trop rapprochée : aucune
        // récompense, aucune progression, le bloc reste strictement
        // inchangé, exactement comme si l'event n'avait jamais eu lieu.
        long now = System.currentTimeMillis();
        long sinceLastBreak = now - island.getLastBreakMillis();
        if (sinceLastBreak < MIN_BREAK_DELAY_MILLIS) {
            return;
        }
        island.setLastBreakMillis(now);

        // Effet de casse (son + particules), pour garder le ressenti d'un
        // vrai bloc cassé puisque Bukkit ne le fait plus lui-même.
        var originalData = broken.getBlockData();
        player.getWorld().playSound(blockLoc, originalData.getSoundGroup().getBreakSound(), 1f, 0.9f);
        player.getWorld().spawnParticle(org.bukkit.Particle.BLOCK,
                blockLoc.clone().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3, originalData);

        // Perte de durabilité de l'outil : comme l'événement est annulé
        // (event.setCancelled(true) plus haut), Bukkit ne fait plus jamais
        // baisser la durabilité tout seul -> les outils ne s'usaient jamais.
        // On applique donc manuellement la même perte que Minecraft vanilla
        // (ItemStack#damage gère lui-même l'enchantement Solidité, l'attribut
        // "incassable" et la casse de l'outil à 0 point de durabilité).
        damageToolInHand(player);

        // 1) Récompense pour le bloc qui vient d'être cassé
        giveCurrentBlockReward(player, island, blockLoc);

        // 2) Progression
        // Boost staff "x2 XP" (/boost give x2xp <joueur>, voir BoostManager) :
        // pendant les 10 minutes du boost, ce bloc cassé compte pour 2 dans
        // la progression de l'île au lieu d'un seul.
        int xpMultiplier = plugin.getBoostManager().getXpMultiplier(uuid);
        int countBeforeBreak = island.getBrokenCount();
        island.incrementBroken(xpMultiplier);
        OneBlockLevel newLevel = manager.getLevelForCount(island.getBrokenCount());
        OneBlockLevel oldLevel = manager.getLevelForCount(countBeforeBreak);
        if (newLevel != null && oldLevel != null && newLevel.getId() != oldLevel.getId()) {
            String msg = plugin.getConfig().getString("levelup-message", "&6Niveau {level} !")
                    .replace("{level}", String.valueOf(newLevel.getId()))
                    .replace("{name}", newLevel.getName());
            player.sendMessage(legacy.deserialize(msg));

            // Récompense automatique dans la caisse d'île à chaque montée de niveau
            // (le bonus "bank-boost" acheté via /upgrades augmente ce montant)
            double reward = manager.getLevelUpBankReward(newLevel.getId()) * manager.getBankBoostMultiplier(island);
            if (reward > 0) {
                island.addBankBalance(reward);
                String bankMsg = manager.getBankLevelupMessage()
                        .replace("{amount}", String.valueOf((long) reward))
                        .replace("{level}", String.valueOf(newLevel.getId()));
                player.sendMessage(legacy.deserialize(bankMsg));
            }
        }

        // 3) Le bloc devient vide pendant le compte à rebours
        blockLoc.getBlock().setType(Material.AIR);
        island.setRegenerating(true);

        plugin.getBossBarManager().update(player, island);

        // Le bloc réapparaît toujours immédiatement, sans compte à rebours ni message.
        manager.regenerateBlock(island, newLevel);
        plugin.getBossBarManager().update(player, island);
    }

    /**
     * Gère la casse du bloc OneBlock Nether ou End d'une île : plus simple
     * que le cycle overworld (pas de niveaux/loot-table, pas de coffre
     * bonus, pas de spawner) — juste le drop vanilla du bloc courant, le
     * comptage, la mémorisation du type pour la condition de renaissance, et
     * la régénération immédiate.
     */
    private void handleDimensionBreak(BlockBreakEvent event, Player player, UUID uuid, IslandData island, boolean isNether) {
        Block broken = event.getBlock();
        Location blockLoc = isNether ? island.getNetherBlockLocation() : island.getEndBlockLocation();

        if (!player.isOp() && !island.canBreak(uuid)) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de casser le bloc OneBlock de cette île."));
            return;
        }

        event.setCancelled(true);

        boolean regenerating = isNether ? island.isNetherRegenerating() : island.isEndRegenerating();
        if (regenerating) return;

        Material expected = isNether ? island.getNetherCurrentMaterial() : island.getEndCurrentMaterial();
        if (expected == null || broken.getType() != expected) {
            player.sendMessage(legacy.deserialize("&cCe bloc ne correspond pas au bloc OneBlock attendu, il a été restauré."));
            if (blockLoc != null) blockLoc.getBlock().setType(expected != null ? expected : broken.getType());
            return;
        }

        long now = System.currentTimeMillis();
        long sinceLastBreak = now - island.getLastBreakMillis();
        if (sinceLastBreak < MIN_BREAK_DELAY_MILLIS) return;
        island.setLastBreakMillis(now);

        var originalData = broken.getBlockData();
        player.getWorld().playSound(blockLoc, originalData.getSoundGroup().getBreakSound(), 1f, 0.9f);
        player.getWorld().spawnParticle(org.bukkit.Particle.BLOCK,
                blockLoc.clone().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3, originalData);

        damageToolInHand(player);

        giveItem(player, getVanillaDrop(expected), blockLoc);

        // Boost staff "x2 XP" (voir BoostManager) : ce bloc compte pour 2
        // dans la progression Nether/End de l'île pendant les 10 minutes du boost.
        int xpMultiplier = plugin.getBoostManager().getXpMultiplier(uuid);
        if (isNether) {
            manager.regenerateNetherBlock(island, xpMultiplier);
        } else {
            manager.regenerateEndBlock(island, xpMultiplier);
        }
    }

    /**
     * Empêche totalement de poser un bloc à la main sur l'emplacement du
     * bloc OneBlock (protection supplémentaire contre l'exploit de
     * duplication : même si le bloc venait à rester vide un court instant,
     * un joueur ne peut plus y glisser un bloc à lui pour le faire passer
     * pour le bloc OneBlock).
     */
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Location loc = event.getBlock().getLocation();
        IslandData island = manager.findIslandAt(loc);
        if (island != null && isSameBlock(loc, island.getBlockLocation())) {
            event.setCancelled(true);
            return;
        }
        if (findIslandByNetherBlock(loc) != null || findIslandByEndBlock(loc) != null) {
            event.setCancelled(true);
        }
    }

    private boolean isSameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private void giveCurrentBlockReward(Player player, IslandData island, Location blockLoc) {
        // COFFRE BONUS (voir OneBlockManager#regenerateBlock/fillChestLoot) :
        // au lieu d'un drop unique, on vide tout le contenu réel du coffre.
        // Pas de "double-break"/"loot-boost" ici, le coffre est déjà lui-même
        // un bonus, et son contenu ne contient jamais d'objet rare.
        if (island.getCurrentMaterial() == Material.CHEST) {
            giveChestContents(player, blockLoc);
            return;
        }

        ItemStack reward = island.getCurrentSpawnerEntity() != null
                ? new ItemStack(Material.SPAWNER)
                : getVanillaDrop(island.getCurrentMaterial());

        giveItem(player, reward, blockLoc);

        // "double-break" acheté via /rebirthshop : double garanti à chaque
        // casse. Sinon, bonus "loot-boost" acheté via /upgrades : chance
        // d'obtenir le même bloc en double à chaque casse.
        if (manager.isDoubleBreakActive(island)) {
            giveItem(player, reward.clone(), blockLoc);
        } else {
            double lootBoostChance = manager.getLootBoostChance(island);
            if (lootBoostChance > 0 && Math.random() * 100.0 < lootBoostChance) {
                giveItem(player, reward.clone(), blockLoc);
            }
        }
    }

    /**
     * Fait tomber au sol tout le contenu réel du coffre bonus (rempli par
     * OneBlockManager#fillChestLoot lors de la régénération), puis le vide.
     * Appelée AVANT que le bloc ne soit remplacé (setType(AIR) dans
     * onBreak), pour que le BlockState "Chest" soit encore valide ici.
     */
    private void giveChestContents(Player player, Location blockLoc) {
        Block block = blockLoc.getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest chest)) return;

        var inv = chest.getBlockInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                giveItem(player, item.clone(), blockLoc);
            }
        }
        inv.clear();
    }

    /**
     * Convertit un bloc en son "drop" naturel, exactement comme en Minecraft
     * vanilla à la main (sans Silk Touch) : pierre -> cobblestone, minerai de
     * fer -> fer brut, minerai d'or -> or brut, etc. Les blocs qui, en
     * vanilla, se droppent eux-mêmes (dirt, sable, bûche, obsidienne...)
     * restent inchangés.
     */
    private ItemStack getVanillaDrop(Material material) {
        switch (material) {
            case STONE:
            case COBBLESTONE:
                return new ItemStack(Material.COBBLESTONE);
            case GRASS_BLOCK:
                return new ItemStack(Material.DIRT);
            case COAL_ORE:
            case DEEPSLATE_COAL_ORE:
                return new ItemStack(Material.COAL);
            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
                return new ItemStack(Material.RAW_IRON);
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
                return new ItemStack(Material.RAW_GOLD);
            case COPPER_ORE:
            case DEEPSLATE_COPPER_ORE:
                return new ItemStack(Material.RAW_COPPER);
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
                return new ItemStack(Material.DIAMOND);
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                return new ItemStack(Material.EMERALD);
            case LAPIS_ORE:
            case DEEPSLATE_LAPIS_ORE:
                return new ItemStack(Material.LAPIS_LAZULI, randomBetween(4, 9));
            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
                return new ItemStack(Material.REDSTONE, randomBetween(4, 5));
            case NETHER_QUARTZ_ORE:
                return new ItemStack(Material.QUARTZ);
            case NETHER_GOLD_ORE:
                return new ItemStack(Material.GOLD_NUGGET, randomBetween(2, 6));
            case GLOWSTONE:
                return new ItemStack(Material.GLOWSTONE_DUST, randomBetween(2, 4));
            case MELON:
                return new ItemStack(Material.MELON_SLICE, randomBetween(3, 7));
            default:
                // Bloc qui, en vanilla, se droppe lui-même (dirt, sable, bois,
                // obsidienne, netherrack, glace, spawner géré à part, etc.)
                return new ItemStack(material);
        }
    }

    private int randomBetween(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }

    /**
     * Dépose l'item au sol, 1 bloc au-dessus du bloc central OneBlock,
     * exactement comme un vrai bloc cassé en vanilla (au lieu de le donner
     * directement dans l'inventaire du joueur).
     */
    private void giveItem(Player player, ItemStack item, Location blockLoc) {
        Location dropLoc = blockLoc.clone().add(0.5, 1.0, 0.5);

        // Boost staff "x2 objets" (/boost give x2item <joueur>, voir BoostManager) :
        // au lieu de doubler la quantité dans le même ItemStack (qui pourrait
        // dépasser la taille de pile max, ex. 32 fer -> 64 c'est bon mais 40 -> 80
        // ne l'est pas), on lâche l'item une seconde fois : simple et toujours valide.
        int multiplier = plugin.getBoostManager().getItemMultiplier(player.getUniqueId());
        for (int i = 0; i < multiplier; i++) {
            player.getWorld().dropItemNaturally(dropLoc, item.clone());
        }
    }

    /**
     * Applique manuellement l'usure de l'outil tenu en main, puisque
     * l'événement de casse est annulé (voir onBreak) et que Bukkit ne gère
     * donc plus du tout la durabilité tout seul.
     *
     * ATTENTION : ItemStack#damage(int, LivingEntity) n'existe PAS dans
     * l'API Bukkit/Paper : ce n'est pas une méthode de la classe ItemStack.
     * Cette erreur empêchait tout le plugin de compiler, donc AUCUNE des
     * corrections de ce fichier ne pouvait s'appliquer en jeu (ni la
     * durabilité, ni le drop 1 bloc au-dessus). La bonne méthode se trouve
     * sur LivingEntity : Player#damageItemStack(slot, amount), qui gère
     * elle-même l'enchantement Solidité (Unbreaking), l'attribut
     * "incassable" et casse proprement l'outil à 0 point de durabilité.
     */
    private void damageToolInHand(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType() == Material.AIR) return;
        if (tool.getType().getMaxDurability() <= 0) return;

        player.damageItemStack(org.bukkit.inventory.EquipmentSlot.HAND, 1);
    }
}
