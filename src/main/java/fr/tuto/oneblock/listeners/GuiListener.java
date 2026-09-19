package fr.tuto.oneblock.listeners;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.gui.AfkGUI;
import fr.tuto.oneblock.gui.AfkGuiHolder;
import fr.tuto.oneblock.gui.BankisItemsGuiHolder;
import fr.tuto.oneblock.gui.OBMenuGUI;
import fr.tuto.oneblock.gui.OBMenuGuiHolder;
import fr.tuto.oneblock.gui.OneBlockGuiHolder;
import fr.tuto.oneblock.gui.RebirthShopGUI;
import fr.tuto.oneblock.gui.RebirthShopGuiHolder;
import fr.tuto.oneblock.gui.SettingsGUI;
import fr.tuto.oneblock.gui.RolePermissionsGUI;
import fr.tuto.oneblock.gui.RolePermissionsGuiHolder;
import fr.tuto.oneblock.models.IslandPermission;
import fr.tuto.oneblock.models.Role;
import fr.tuto.oneblock.gui.TokenShopGUI;
import fr.tuto.oneblock.gui.TokenShopGuiHolder;
import fr.tuto.oneblock.gui.TopGUI;
import fr.tuto.oneblock.gui.TopGuiHolder;
import fr.tuto.oneblock.gui.UpgradesGUI;
import fr.tuto.oneblock.gui.UpgradesGuiHolder;
import fr.tuto.oneblock.managers.VaultManager;
import fr.tuto.oneblock.models.IslandData;
import fr.tuto.oneblock.models.ShopUpgrade;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class GuiListener implements Listener {

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public GuiListener(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof AfkGuiHolder afkHolder) {
            handleAfkClick(event, afkHolder);
            return;
        }

        if (event.getInventory().getHolder() instanceof TokenShopGuiHolder shopHolder) {
            handleTokenShopClick(event, shopHolder);
            return;
        }

        if (event.getInventory().getHolder() instanceof UpgradesGuiHolder upgradesHolder) {
            handleUpgradesClick(event, upgradesHolder);
            return;
        }

        if (event.getInventory().getHolder() instanceof RebirthShopGuiHolder rebirthShopHolder) {
            handleRebirthShopClick(event, rebirthShopHolder);
            return;
        }

        if (event.getInventory().getHolder() instanceof TopGuiHolder topHolder) {
            handleTopClick(event, topHolder);
            return;
        }

        if (event.getInventory().getHolder() instanceof RolePermissionsGuiHolder permsHolder) {
            handleRolePermissionsClick(event, permsHolder);
            return;
        }

        if (event.getInventory().getHolder() instanceof OBMenuGuiHolder menuHolder) {
            handleOBMenuClick(event, menuHolder);
            return;
        }

        if (!(event.getInventory().getHolder() instanceof OneBlockGuiHolder holder)) return;

        event.setCancelled(true); // on ne laisse jamais prendre les items du GUI

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        Player player = holder.getOwner();
        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island == null) return;

        String clickedKey = null;
        for (Map.Entry<String, Integer> entry : SettingsGUI.getSlots().entrySet()) {
            if (entry.getValue() == slot) {
                clickedKey = entry.getKey();
                break;
            }
        }
        if (clickedKey == null) return; // clic sur une bordure décorative

        org.bukkit.World playerWorld = player.getWorld();
        boolean current = island.getSetting(playerWorld, clickedKey, plugin.getManager().getDefaultSettings().getOrDefault(clickedKey, true));
        boolean newValue = !current;

        // Le vol ("fly") reste bloqué en OFF tant qu'il n'a pas été acheté
        // avec des tokens via /token.
        if (clickedKey.equals("fly") && newValue && !island.isFlyUnlocked()) {
            player.sendMessage(legacy.deserialize("&cLe vol est verrouillé ! Débloque-le dans la boutique: &f/token"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Le PVP ne peut être basculé qu'une fois toutes les 10 minutes par
        // île, pour empêcher les allers-retours abusifs (ex: l'activer
        // juste pour frapper quelqu'un puis le redésactiver aussitôt).
        if (clickedKey.equals("pvp")) {
            long now = System.currentTimeMillis();
            long cooldownMillis = 10L * 60L * 1000L;
            long elapsed = now - island.getLastPvpToggleMillis();
            if (elapsed < cooldownMillis) {
                long remainingSeconds = (cooldownMillis - elapsed) / 1000L;
                long minutes = remainingSeconds / 60L;
                long seconds = remainingSeconds % 60L;
                player.sendMessage(legacy.deserialize(
                        "&cTu dois attendre encore &f" + minutes + "m" + seconds + "s"
                                + " &cavant de pouvoir changer le PVP à nouveau."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            island.setLastPvpToggleMillis(now);
        }

        island.setSetting(playerWorld, clickedKey, newValue);

        if (clickedKey.equals("fly")) {
            player.setAllowFlight(newValue);
            if (!newValue && player.isFlying()) {
                player.setFlying(false);
            }
        } else if (clickedKey.equals("always-day")) {
            plugin.getManager().applyTimeLock(player, island);
        } else if (clickedKey.equals("border-particles")) {
            plugin.getManager().applyBorder(player, island);
        }

        SettingsGUI.refresh(plugin, event.getInventory(), island, playerWorld);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, current ? 0.7f : 1.3f);
    }

    /**
     * Le coffre partagé de la caisse d'île (/bankis objets) n'est jamais
     * annulé au clic (voir BankisItemsGuiHolder) : les joueurs y déposent et
     * en retirent librement des objets une fois l'accès autorisé à
     * l'ouverture. On resynchronise simplement son contenu vers IslandData
     * (et on sauvegarde sur disque) à chaque fermeture, pour ne rien perdre
     * en cas de crash serveur entre deux sauvegardes automatiques.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BankisItemsGuiHolder holder)) return;

        IslandData island = plugin.getManager().getIsland(holder.getIslandOwner());
        if (island == null) return;

        island.setBankItems(event.getInventory().getContents());
        plugin.getManager().saveIslands();
    }

    private void handleAfkClick(InventoryClickEvent event, AfkGuiHolder holder) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot != AfkGUI.CLAIM_SLOT) return; // seul le bouton de récupération est cliquable

        Player player = holder.getOwner();
        boolean claimed = plugin.getAfkZoneManager().claim(player);

        if (claimed) {
            player.sendMessage(legacy.deserialize("&a✔ Récompenses de la zone AFK récupérées !"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        } else {
            player.sendMessage(legacy.deserialize("&cTu n'as aucune récompense à récupérer pour l'instant."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        AfkGUI.refresh(plugin, event.getInventory(), player);
    }

    private void handleTokenShopClick(InventoryClickEvent event, TokenShopGuiHolder holder) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        Player player = holder.getOwner();
        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island == null) return;

        String clickedKey = null;
        for (Map.Entry<String, Integer> entry : TokenShopGUI.getSlots().entrySet()) {
            if (entry.getValue() == slot) {
                clickedKey = entry.getKey();
                break;
            }
        }
        if (clickedKey == null) return; // clic sur la bordure ou l'item de solde

        if (TokenShopGUI.isUnlocked(island, clickedKey)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.7f);
            return; // déjà possédé, rien à faire
        }

        var shopItem = TokenShopGUI.getItem(clickedKey);
        String name = shopItem != null ? shopItem.displayName() : clickedKey;

        int cost = TokenShopGUI.getCost(plugin, clickedKey);
        int balance = island.getTokens(player.getUniqueId());
        if (balance < cost) {
            String msgText = plugin.getTokenConfig().getString(
                            "token-messages.no-tokens", "&cIl te faut &b{cost} tokens &c(tu as &b{balance}&c).")
                    .replace("{cost}", String.valueOf(cost))
                    .replace("{balance}", String.valueOf(balance));
            player.sendMessage(legacy.deserialize(msgText));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        island.addTokens(player.getUniqueId(), -cost);
        if (clickedKey.equals("fly")) {
            island.setFlyUnlocked(true);
        }
        if (clickedKey.equals("hat")) {
            island.setHatUnlocked(true);
            // Donne l'accès à la commande /hat via une permission attachée au joueur.
            player.addAttachment(plugin, "essentials.hat", true);
        }
        if (clickedKey.equals("feed")) {
            island.setFeedUnlocked(true);
            // Donne l'accès à la commande /feed via une permission attachée au joueur.
            player.addAttachment(plugin, "essentials.feed", true);
        }
        if (clickedKey.equals("cle_commune")) {
            // Article consommable : donne directement une clé Commune via ecrates,
            // achetable à volonté (pas de statut "débloqué" à mémoriser).
            plugin.getAfkZoneManager().giveEcratesKey(player, "commune", 1);
        }
        if (clickedKey.equals("cle_magique")) {
            // Article consommable : donne directement une clé Magique via ecrates,
            // achetable à volonté (pas de statut "débloqué" à mémoriser).
            // ⚠️ Adapte "magique" si ton type de clé ecrates porte un autre id (ex: "magical").
            plugin.getAfkZoneManager().giveEcratesKey(player, "magique", 1);
        }
        if (clickedKey.equals("balise")) {
            // Article consommable : donne directement une Balise (item vanilla),
            // achetable à volonté (pas de statut "débloqué" à mémoriser).
            ItemStack beacon = new ItemStack(Material.BEACON, 1);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(beacon);
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
        if (clickedKey.equals("argent")) {
            // Article consommable : crédite directement le compte du joueur via
            // Vault, achetable à volonté (pas de statut "débloqué" à mémoriser).
            plugin.getVaultManager().deposit(player, 500000);
        }

        String successMsg;
        if (TokenShopGUI.isConsumable(clickedKey)) {
            successMsg = plugin.getTokenConfig().getString(
                            "token-messages.success-item", "&a✔ Tu as reçu &b{name} &a!")
                    .replace("{name}", name);
        } else {
            successMsg = plugin.getTokenConfig().getString(
                            "token-messages.success", "&a✔ &b{name} &adébloqué !")
                    .replace("{name}", name);
        }
        player.sendMessage(legacy.deserialize(successMsg));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

        TokenShopGUI.refresh(plugin, event.getInventory(), player, island);
    }

    private void handleUpgradesClick(InventoryClickEvent event, UpgradesGuiHolder holder) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        Player player = holder.getOwner();
        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island == null) return;

        String clickedKey = null;
        for (Map.Entry<String, Integer> entry : UpgradesGUI.getSlots(plugin).entrySet()) {
            if (entry.getValue() == slot) {
                clickedKey = entry.getKey();
                break;
            }
        }
        if (clickedKey == null) return; // clic sur la bordure ou l'item de solde

        var manager = plugin.getManager();
        ShopUpgrade def = manager.getUpgrades().get(clickedKey);
        if (def == null) return;

        int level = island.getUpgradeLevel(clickedKey);
        if (level >= def.maxLevel()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.7f);
            return; // déjà au niveau max
        }

        VaultManager vault = plugin.getVaultManager();
        if (!vault.isEnabled()) {
            player.sendMessage(legacy.deserialize(plugin.getConfig().getString(
                    "upgrades-messages.no-economy", "&cAucun plugin d'économie (Vault) n'est installé.")));
            return;
        }

        double cost = manager.getUpgradeCost(island, def);
        if (!vault.has(player, cost)) {
            String msgText = plugin.getConfig().getString(
                            "upgrades-messages.no-money", "&cIl te faut &e{cost}$ &c(tu as &e{balance}$&c).")
                    .replace("{cost}", vault.format(cost))
                    .replace("{balance}", vault.format(vault.getBalance(player)));
            player.sendMessage(legacy.deserialize(msgText));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!vault.withdraw(player, cost)) {
            player.sendMessage(legacy.deserialize("&cLa transaction a échoué, réessaie."));
            return;
        }

        island.setUpgradeLevel(clickedKey, level + 1);

        String successMsg = plugin.getConfig().getString(
                        "upgrades-messages.success", "&a✔ &d{name} &aest maintenant niveau &e{level}&a.")
                .replace("{name}", def.name())
                .replace("{level}", String.valueOf(level + 1));
        player.sendMessage(legacy.deserialize(successMsg));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

        UpgradesGUI.refresh(plugin, event.getInventory(), island);
    }

    private void handleRebirthShopClick(InventoryClickEvent event, RebirthShopGuiHolder holder) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        Player player = holder.getOwner();
        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island == null) return;

        String clickedKey = null;
        for (Map.Entry<String, Integer> entry : RebirthShopGUI.getSlots(plugin).entrySet()) {
            if (entry.getValue() == slot) {
                clickedKey = entry.getKey();
                break;
            }
        }
        if (clickedKey == null) return; // clic sur la bordure ou l'item de solde

        var manager = plugin.getManager();
        ShopUpgrade def = manager.getRebirthShopItems().get(clickedKey);
        if (def == null) return;

        int level = island.getRebirthShopLevel(clickedKey);

        // Articles répétables (ex: "instant-money") : pas de palier max, prix
        // fixe, effet immédiat à chaque achat (voir ShopUpgrade#repeatable).
        if (def.repeatable()) {
            int cost = (int) Math.ceil(def.baseCost());
            if (island.getRebirthPoints() < cost) {
                String msgText = plugin.getRebirthConfig().getString(
                                "rebirth-shop-messages.no-points", "&cIl te faut &d{cost} points de renaissance &c(tu en as &d{balance}&c).")
                        .replace("{cost}", String.valueOf(cost))
                        .replace("{balance}", String.valueOf(island.getRebirthPoints()));
                player.sendMessage(legacy.deserialize(msgText));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            island.addRebirthPoints(-cost);
            island.setRebirthShopLevel(clickedKey, level + 1); // sert de compteur d'achats

            applyRepeatableRebirthShopEffect(clickedKey, def, player);

            String purchasedMsg = plugin.getRebirthConfig().getString(
                            "rebirth-shop-messages.purchased", "&a✔ &d{name} &aacheté ! &7(&a+{amount}$&7)")
                    .replace("{name}", def.name())
                    .replace("{amount}", String.valueOf((long) def.bonusPerLevel()));
            player.sendMessage(legacy.deserialize(purchasedMsg));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

            RebirthShopGUI.refresh(plugin, event.getInventory(), island);
            return;
        }

        if (level >= def.maxLevel()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.7f);
            return; // déjà au niveau max
        }

        int cost = manager.getRebirthShopCost(island, def);
        if (island.getRebirthPoints() < cost) {
            String msgText = plugin.getRebirthConfig().getString(
                            "rebirth-shop-messages.no-points", "&cIl te faut &d{cost} points de renaissance &c(tu en as &d{balance}&c).")
                    .replace("{cost}", String.valueOf(cost))
                    .replace("{balance}", String.valueOf(island.getRebirthPoints()));
            player.sendMessage(legacy.deserialize(msgText));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        island.addRebirthPoints(-cost);
        island.setRebirthShopLevel(clickedKey, level + 1);

        String successMsg = plugin.getRebirthConfig().getString(
                        "rebirth-shop-messages.success", "&a✔ &d{name} &aest maintenant niveau &e{level}&a.")
                .replace("{name}", def.name())
                .replace("{level}", String.valueOf(level + 1));
        player.sendMessage(legacy.deserialize(successMsg));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

        RebirthShopGUI.refresh(plugin, event.getInventory(), island);
    }

    /**
     * Applique l'effet immédiat d'un article répétable de /rebirthshop.
     * Ajoute une nouvelle clé ici pour tout futur article répétable.
     */
    private void applyRepeatableRebirthShopEffect(String key, ShopUpgrade def, Player player) {
        if (key.equals("instant-money")) {
            VaultManager vault = plugin.getVaultManager();
            if (vault != null && vault.isEnabled()) {
                vault.deposit(player, def.bonusPerLevel());
            }
        }
    }

    private void handleRolePermissionsClick(InventoryClickEvent event, RolePermissionsGuiHolder holder) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        Player player = holder.getOwner();
        // Sécurité : seul le propriétaire de l'île peut modifier les permissions,
        // même si son statut a changé (kick/ban) depuis l'ouverture du GUI.
        if (!plugin.getManager().isOwner(player.getUniqueId()) || !holder.getIslandOwner().equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island == null) return;

        // Changement d'onglet de rôle.
        if (RolePermissionsGUI.isTabVisiteurSlot(slot)) {
            holder.setActiveRole(Role.VISITEUR);
            RolePermissionsGUI.refresh(event.getInventory(), island, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }
        if (RolePermissionsGUI.isTabTrustSlot(slot)) {
            holder.setActiveRole(Role.TRUST);
            RolePermissionsGUI.refresh(event.getInventory(), island, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }
        if (RolePermissionsGUI.isTabMembreSlot(slot)) {
            holder.setActiveRole(Role.MEMBRE);
            RolePermissionsGUI.refresh(event.getInventory(), island, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        IslandPermission perm = RolePermissionsGUI.resolveClick(slot);
        if (perm == null) return; // clic sur un onglet, l'info, ou une bordure décorative

        Role role = holder.getActiveRole();
        boolean current = island.getRolePermission(role, perm.getKey());
        island.setRolePermission(role, perm.getKey(), !current);

        RolePermissionsGUI.refresh(event.getInventory(), island, holder);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, current ? 0.7f : 1.3f);
    }

    private void handleTopClick(InventoryClickEvent event, TopGuiHolder holder) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        String clickedCategory = TopGUI.getCategoryButtons().get(slot);
        if (clickedCategory == null) return; // clic sur la bordure ou une tête de joueur

        Player player = holder.getOwner();

        if (clickedCategory.equals(holder.getCategory())) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.7f);
            return; // déjà affiché
        }

        holder.setCategory(clickedCategory);
        TopGUI.refresh(plugin, event.getInventory(), clickedCategory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    /**
     * Clics dans le GUI {@code /ob menu} : les onglets "Visite"/"Sanction"
     * changent juste la catégorie affichée, un clic sur une tête de joueur
     * déclenche l'action correspondante en déléguant à la sous-commande
     * /ob concernée (visit/trust/untrust/ban/unban/kick), pour bénéficier
     * exactement des mêmes vérifications et messages que ces commandes.
     */
    private void handleOBMenuClick(InventoryClickEvent event, OBMenuGuiHolder holder) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        Player player = holder.getOwner();

        if (slot == OBMenuGUI.TAB_VISITE_SLOT || slot == OBMenuGUI.TAB_SANCTION_SLOT) {
            OBMenuGuiHolder.Category clicked = slot == OBMenuGUI.TAB_VISITE_SLOT
                    ? OBMenuGuiHolder.Category.VISITE
                    : OBMenuGuiHolder.Category.SANCTION;
            if (clicked == holder.getCategory()) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.7f);
                return;
            }
            holder.setCategory(clicked);
            OBMenuGUI.refresh(plugin, event.getInventory(), player, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            return;
        }

        Map<Integer, java.util.UUID> targets = holder.getSlotTargets();
        java.util.UUID targetUuid = targets == null ? null : targets.get(slot);
        if (targetUuid == null) return; // clic sur une bordure décorative

        org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetUuid);
        String name = target.getName();
        if (name == null) return;

        boolean shift = event.isShiftClick();
        String subCommand;
        if (holder.getCategory() == OBMenuGuiHolder.Category.VISITE) {
            if (event.isLeftClick()) {
                subCommand = "visit " + name;
            } else if (shift) {
                subCommand = "untrust " + name;
            } else {
                subCommand = "trust " + name;
            }
        } else {
            if (shift && event.isLeftClick()) {
                subCommand = "kick " + name;
            } else if (event.isLeftClick()) {
                subCommand = "ban " + name;
            } else {
                subCommand = "unban " + name;
            }
        }

        player.closeInventory();
        plugin.getServer().dispatchCommand(player, "ob " + subCommand);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }
}

