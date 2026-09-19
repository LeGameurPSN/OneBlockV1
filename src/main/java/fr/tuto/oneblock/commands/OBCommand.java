package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import fr.tuto.oneblock.models.OneBlockLevel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class OBCommand implements CommandExecutor, TabCompleter {

    /** Termes disponibles en premier argument de /ob (utilisé pour l'aide et l'auto-complétion). */
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "start", "reset", "niveau", "level", "stop", "tp",
            "agrandir", "expand", "settings", "parametres", "visit", "visite",
            "renaissance", "rebirth", "team", "kick", "ban", "unban",
            "permissions", "perms", "leave", "chat",
            "lock", "verrouiller", "unlock", "deverrouiller",
            "trust", "untrust", "menu"
    );

    private final OneBlockPlugin plugin;
    private final OneBlockManager manager;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    // Confirmations en attente pour /ob leave (uuid -> instant d'expiration en ms).
    private final Map<UUID, Long> pendingLeaveConfirm = new HashMap<>();
    private static final long LEAVE_CONFIRM_WINDOW_MS = 30_000;

    public OBCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande doit être exécutée par un joueur.");
            return true;
        }

        if (!player.hasPermission("oneblock.use")) {
            msg(player, "&cTu n'as pas la permission d'utiliser le OneBlock.");
            return true;
        }

        // /is (ou /island) : raccourci direct vers /ob tp / /ob visit / /ob lock / /ob unlock
        if (label.equalsIgnoreCase("is") || label.equalsIgnoreCase("island")) {
            if (args.length >= 2 && (args[0].equalsIgnoreCase("visit") || args[0].equalsIgnoreCase("visite"))) {
                visitIsland(player, args);
            } else if (args.length >= 1 && (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("verrouiller"))) {
                setLocked(player, true);
            } else if (args.length >= 1 && (args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("deverrouiller"))) {
                setLocked(player, false);
            } else {
                teleportBack(player);
            }
            return true;
        }

        // /rebirth : raccourci direct vers /ob renaissance, utilisable à
        // tout moment (une fois le niveau requis atteint, voir rebirthIsland)
        if (label.equalsIgnoreCase("rebirth") || label.equalsIgnoreCase("renaissance")) {
            rebirthIsland(player);
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        String node = subPermission(sub);
        if (node != null && !player.hasPermission(node)) {
            msg(player, "&cTu n'as pas la permission d'utiliser cette sous-commande (&7" + node + "&c).");
            return true;
        }

        switch (sub) {
            case "start" -> startIsland(player);
            case "reset" -> resetIsland(player);
            case "niveau", "level" -> showLevel(player);
            case "stop", "tp" -> teleportBack(player);
            case "agrandir", "expand" -> expandIsland(player);
            case "settings", "parametres" -> handleSettings(player, args);
            case "visit", "visite" -> visitIsland(player, args);
            case "renaissance", "rebirth" -> rebirthIsland(player);
            case "team" -> handleTeam(player, args);
            case "kick" -> kickMember(player, args);
            case "ban" -> banPlayer(player, args);
            case "unban" -> unbanPlayer(player, args);
            case "permissions", "perms" -> openPermissions(player);
            case "leave" -> leaveTeam(player, args);
            case "chat" -> handleTeamChat(player, args);
            case "lock", "verrouiller" -> setLocked(player, true);
            case "unlock", "deverrouiller" -> setLocked(player, false);
            case "trust" -> trustPlayer(player, args);
            case "untrust" -> untrustPlayer(player, args);
            case "menu" -> openMenu(player);
            default -> sendHelp(player);
        }
        return true;
    }

    /**
     * Associe chaque sous-commande de /ob à son nœud de permission précis
     * (voir la section "permissions" de plugin.yml). Retourne null si la
     * sous-commande n'a pas de permission dédiée (ex. commande inconnue,
     * gérée par sendHelp).
     */
    private String subPermission(String sub) {
        return switch (sub) {
            case "start" -> "oneblock.start";
            case "reset" -> "oneblock.reset";
            case "niveau", "level" -> "oneblock.niveau";
            case "stop", "tp" -> "oneblock.tp";
            case "agrandir", "expand" -> "oneblock.agrandir";
            case "settings", "parametres" -> "oneblock.settings";
            case "visit", "visite" -> "oneblock.visit";
            case "renaissance", "rebirth" -> "oneblock.rebirth";
            case "team" -> "oneblock.team";
            case "kick" -> "oneblock.kick";
            case "ban" -> "oneblock.ban";
            case "unban" -> "oneblock.unban";
            case "permissions", "perms" -> "oneblock.permissions";
            case "leave" -> "oneblock.leave";
            case "chat" -> "oneblock.chat";
            case "lock", "verrouiller", "unlock", "deverrouiller" -> "oneblock.lock";
            case "trust", "untrust" -> "oneblock.trust";
            case "menu" -> "oneblock.menu";
            default -> null;
        };
    }

    private void startIsland(Player player) {
        UUID uuid = player.getUniqueId();
        if (manager.isOwner(uuid)) {
            msg(player, "&eTu as déjà une île OneBlock ! Utilise &f/ob tp &epour y retourner ou &f/ob reset &epour la réinitialiser.");
            return;
        }
        if (manager.hasIsland(uuid)) {
            msg(player, "&eTu fais déjà partie de l'équipe d'une île ! Utilise &f/ob tp &epour y retourner, ou &f/ob leave &epour la quitter et créer la tienne.");
            return;
        }
        IslandData island = manager.createIsland(uuid, player.getName());
        teleportOnTop(player, island);
        msg(player, "&a✔ Ton île OneBlock a été créée ! Casse le bloc sous tes pieds pour commencer.");
    }

    private void resetIsland(Player player) {
        UUID uuid = player.getUniqueId();
        if (!manager.isOwner(uuid)) {
            if (manager.hasIsland(uuid)) {
                msg(player, "&cTu n'es pas propriétaire de cette île, tu ne peux pas la réinitialiser. Utilise &f/ob leave &cpour la quitter et créer la tienne.");
            } else {
                msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            }
            return;
        }
        IslandData island = manager.resetIsland(uuid, player.getName());
        teleportOnTop(player, island);
        msg(player, "&a✔ Ton île OneBlock a été entièrement réinitialisée : terrain, constructions, améliorations (/upgrades, /rebirthshop) et renaissances sont remis à zéro.");
    }

    private void teleportBack(Player player) {
        IslandData island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }
        teleportOnTop(player, island);
    }

    private void expandIsland(Player player) {
        IslandData island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }

        int max = manager.getBorderMaxSize();
        if (max > 0 && island.getBorderSize() >= max) {
            msg(player, plugin.getConfig().getString("border-expand-max", "&cTaille maximum atteinte.")
                    .replace("{max}", String.valueOf(max)));
            return;
        }

        var vault = plugin.getVaultManager();
        if (!vault.isEnabled()) {
            msg(player, plugin.getConfig().getString("border-no-economy", "&cAucune économie détectée."));
            return;
        }

        double cost = manager.getBorderExpandCost();
        if (!vault.has(player, cost)) {
            msg(player, plugin.getConfig().getString("border-expand-no-money", "&cIl te faut {cost}$.")
                    .replace("{cost}", vault.format(cost))
                    .replace("{balance}", vault.format(vault.getBalance(player))));
            return;
        }

        if (!vault.withdraw(player, cost)) {
            msg(player, "&cLa transaction a échoué, réessaie.");
            return;
        }

        int newSize = island.getBorderSize() + manager.getBorderExpandAmount();
        if (max > 0 && newSize > max) newSize = max;
        island.setBorderSize(newSize);
        manager.applyBorder(player, island);

        msg(player, plugin.getConfig().getString("border-expand-success", "&aÎle agrandie ! Taille : {size}")
                .replace("{size}", String.valueOf(newSize))
                .replace("{cost}", vault.format(cost)));
    }

    private void showLevel(Player player) {
        IslandData island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }
        OneBlockLevel level = manager.getLevelForCount(island.getBrokenCount());
        String name = level != null ? level.getName() : "?";
        int id = level != null ? level.getId() : 0;
        int max = level != null ? level.getMaxBlocks() : -1;

        msg(player, "&6=== Progression OneBlock ===");
        msg(player, "&7Niveau actuel: &e" + id + " &7(&f" + name + "&7)");
        msg(player, "&7Blocs cassés: &e" + island.getBrokenCount() + (max >= 0 ? "&7/&e" + max : ""));
        if (island.getRebirths() > 0) {
            msg(player, "&7Renaissances: &d" + island.getRebirths());
        }
        if (island.getRebirthPoints() > 0) {
            msg(player, "&7Points de renaissance: &d" + island.getRebirthPoints() + " &7(&f/rebirthshop&7)");
        }
        msg(player, "&7Caisse d'île: &e" + plugin.getVaultManager().format(island.getBankBalance()) + " &7(&f/bankis&7)");
    }

    private void rebirthIsland(Player player) {
        IslandData island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }
        if (!manager.isRebirthEnabled()) {
            msg(player, plugin.getRebirthConfig().getString("rebirth.disabled-message", "&cLe système de renaissance est désactivé sur ce serveur."));
            return;
        }

        int requiredId = manager.getRebirthRequiredLevel();
        OneBlockLevel currentLevel = manager.getLevelForCount(island.getBrokenCount());
        boolean overworldReady = currentLevel != null && currentLevel.getId() >= requiredId;
        boolean netherReady = manager.isNetherRebirthReady(island);
        boolean endReady = manager.isEndRebirthReady(island);

        if (!overworldReady && !netherReady && !endReady) {
            OneBlockLevel requiredLevel = manager.getLevelById(requiredId);
            String requiredName = requiredLevel != null ? requiredLevel.getName() : "?";
            int required = manager.getRebirthNetherEndRequiredBlocks();
            String msgText = plugin.getRebirthConfig().getString("rebirth.requirement-message",
                            "&cTu dois atteindre le niveau &e{required} &c(&f{name}&c) pour pouvoir renaître. Niveau actuel : &e{current}&c.")
                    .replace("{required}", String.valueOf(requiredId))
                    .replace("{name}", requiredName)
                    .replace("{current}", String.valueOf(currentLevel != null ? currentLevel.getId() : 0));
            msg(player, msgText);
            msg(player, "&7Ou casse &e" + required + " &7blocs dans ton Nether (ou ton End) et obtiens-y au moins une fois &etous les blocs pleins &7de cette dimension.");
            return;
        }

        // Ne réinitialise QUE la/les dimension(s) qui ont atteint leur condition
        // de renaissance, pas les autres (voir demande : "reset le level que
        // si is/world où il a le level requis").
        //
        // Le compteur "niveau" (brokenCount) est désormais FUSIONNÉ entre
        // l'Overworld et le Nether (voir IslandData#incrementNetherBroken) :
        // miner dans le Nether fait donc progresser le même niveau global.
        // Renaître dans N'IMPORTE QUELLE dimension (Overworld OU Nether)
        // remet donc ce niveau global à 0 — mais SEUL un rebirth Overworld
        // touche au bloc/build physique de l'île (regenerateBlock) : un
        // rebirth Nether ne fait que réinitialiser les compteurs, jamais le
        // build ni le bloc en cours, ni dans le Nether ni dans l'Overworld.
        java.util.List<String> resetParts = new java.util.ArrayList<>();
        double totalReward = 0;
        boolean resetGlobalLevel = overworldReady || netherReady;

        if (overworldReady) {
            OneBlockLevel firstLevel = manager.getLevelForCount(0);
            manager.regenerateBlock(island, firstLevel);
            totalReward += manager.getRebirthMoneyReward();
            resetParts.add("Overworld");
        }
        if (netherReady) {
            island.setNetherBrokenCount(0);
            island.getNetherMinedTypes().clear();
            totalReward += manager.getRebirthMoneyReward();
            resetParts.add("Nether");
        }
        if (resetGlobalLevel) {
            island.setBrokenCount(0);
        }
        if (endReady) {
            island.setEndBrokenCount(0);
            island.getEndMinedTypes().clear();
            manager.regenerateEndBlock(island);
            totalReward += manager.getRebirthMoneyReward();
            resetParts.add("End");
        }

        island.incrementRebirths();

        // Le bonus "bank-boost" (/upgrades) et "rebirth-reward-boost" (/rebirthshop)
        // augmentent tous les deux la récompense de renaissance.
        double reward = totalReward
                * manager.getBankBoostMultiplier(island)
                * manager.getRebirthRewardBoostMultiplier(island);
        if (reward > 0) {
            island.addBankBalance(reward);
        }

        int points = manager.getRebirthShopPointsPerRebirth();
        if (points > 0) {
            island.addRebirthPoints(points);
        }

        plugin.getBossBarManager().update(player, island);

        String successMsg = plugin.getRebirthConfig().getString("rebirth.success-message",
                        "&d&l✦ Renaissance ! &7Tu repars du niveau 1 &8(renaissance n°{count})&7. &a+{reward}$ &7dans la caisse d'île !")
                .replace("{count}", String.valueOf(island.getRebirths()))
                .replace("{reward}", String.valueOf((long) reward));
        msg(player, successMsg);
        msg(player, "&7Dimension(s) réinitialisée(s) : &e" + String.join(", ", resetParts));
    }

    private void handleSettings(Player player, String[] args) {
        IslandData island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }

        // /ob settings -> ouvre le GUI
        if (args.length == 1) {
            fr.tuto.oneblock.gui.SettingsGUI.open(plugin, player, island);
            return;
        }

        // /ob settings <clé> <true|false> -> reste utilisable en commande (console, macros...)
        var namesSection = plugin.getConfig().getConfigurationSection("settings-names");
        String key = args[1].toLowerCase();
        if (key.equals("keep-inventory")) {
            msg(player, "&cCe paramètre ne peut pas être modifié par les joueurs, il est fixé par le serveur.");
            return;
        }
        if (!manager.getDefaultSettings().containsKey(key)) {
            msg(player, "&cParamètre inconnu: &f" + key);
            return;
        }
        if (args.length < 3) {
            boolean current = island.getSetting(key, true);
            msg(player, "&7Valeur actuelle de &f" + key + "&7 : " + (current ? "&aactivé" : "&cdésactivé"));
            return;
        }
        boolean newValue = Boolean.parseBoolean(args[2]);
        island.setSetting(key, newValue);
        String label = namesSection != null ? namesSection.getString(key, key) : key;
        msg(player, "&a✔ " + label + " : " + (newValue ? "&aactivé" : "&cdésactivé"));
    }

    // ==================== ÉQUIPE (/ob team, /ob kick, /ob ban, /ob permissions, /ob leave) ====================

    private void handleTeam(Player player, String[] args) {
        if (args.length < 2) {
            teamList(player);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "invite" -> teamInvite(player, args);
            case "accept" -> teamAccept(player);
            case "deny", "refuse" -> teamDeny(player);
            case "list" -> teamList(player);
            default -> {
                msg(player, "&cUsage: &f/ob team [invite <pseudo>|accept|deny|list]");
            }
        }
    }

    /**
     * /ob chat : active/désactive le mode "chat d'équipe" (tout le chat classique du
     * joueur part uniquement vers son île tant que le mode reste actif).
     * /ob chat <message> : envoie un seul message à l'équipe sans activer le mode.
     */
    private void handleTeamChat(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        IslandData island = manager.getIsland(uuid);
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }

        if (args.length >= 2) {
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            sendTeamChatMessage(player, island, message);
            return;
        }

        boolean enabled = manager.toggleTeamChat(uuid);
        if (enabled) {
            msg(player, "&a✔ Chat d'équipe activé. Tous tes messages ne seront visibles que par ton équipe. Retape &f/ob chat &apour revenir au chat public.");
        } else {
            msg(player, "&7Chat d'équipe désactivé, tu es revenu au chat public.");
        }
    }

    private void sendTeamChatMessage(Player player, IslandData island, String message) {
        Component formatted = legacy.deserialize("&8[&aÉquipe&8] &f" + player.getName() + "&7: &f" + message);

        java.util.Set<Player> recipients = new java.util.LinkedHashSet<>();
        Player owner = plugin.getServer().getPlayer(island.getOwner());
        if (owner != null) recipients.add(owner);
        for (UUID memberUuid : island.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberUuid);
            if (member != null) recipients.add(member);
        }
        recipients.add(player);

        for (Player recipient : recipients) {
            recipient.sendMessage(formatted);
        }
    }

    private void teamList(Player player) {
        IslandData island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }
        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(island.getOwner());
        msg(player, "&6=== Équipe de l'île de " + owner.getName() + " ===");
        if (island.getMembers().isEmpty()) {
            msg(player, "&7Aucun coéquipier pour le moment.");
        } else {
            for (UUID memberUuid : island.getMembers()) {
                OfflinePlayer member = plugin.getServer().getOfflinePlayer(memberUuid);
                msg(player, "&7- &f" + member.getName() + (member.isOnline() ? " &a●en ligne" : " &8●hors ligne"));
            }
        }
        if (manager.isOwner(player.getUniqueId())) {
            msg(player, "&7Utilise &f/ob team invite <pseudo> &7pour inviter, &f/ob permissions &7pour gérer les droits.");
        }
    }

    private void teamInvite(Player player, String[] args) {
        if (!player.hasPermission("oneblock.team.invite")) {
            msg(player, "&cTu n'as pas la permission d'inviter des joueurs (&7oneblock.team.invite&c).");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!manager.isOwner(uuid)) {
            msg(player, "&cSeul le propriétaire d'une île peut inviter un joueur dans son équipe.");
            return;
        }
        if (args.length < 3) {
            msg(player, "&cUsage: &f/ob team invite <pseudo>");
            return;
        }

        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null) {
            msg(player, "&cJoueur introuvable ou hors ligne: &f" + args[2]);
            return;
        }
        if (target.getUniqueId().equals(uuid)) {
            msg(player, "&cTu ne peux pas t'inviter toi-même.");
            return;
        }

        IslandData island = manager.getIsland(uuid);
        if (island.isMember(target.getUniqueId())) {
            msg(player, "&e" + target.getName() + " &efait déjà partie de ton équipe.");
            return;
        }
        if (island.isBanned(target.getUniqueId())) {
            msg(player, "&cCe joueur est banni de ton île. Utilise &f/ob unban " + target.getName() + " &cd'abord.");
            return;
        }

        manager.invitePlayer(uuid, target.getUniqueId());
        msg(player, "&a✔ Invitation envoyée à &e" + target.getName() + "&a.");
        msg(target, "&6" + player.getName() + " &et'invite à rejoindre son île OneBlock !");
        msg(target, "&7Si tu as ta propre île, &ctout sera entièrement supprimé&7 en rejoignant l'équipe : build, niveau (blocs cassés), bankis, Nether ET End.");
        msg(target, "&a▶ /ob team accept &7pour accepter — &c▶ /ob team deny &7pour refuser &8(expire dans 60s)");
    }

    private void teamAccept(Player player) {
        UUID uuid = player.getUniqueId();
        UUID ownerUuid = manager.getPendingInviteOwner(uuid);
        if (ownerUuid == null) {
            msg(player, "&cTu n'as aucune invitation d'équipe en attente.");
            return;
        }
        manager.clearPendingInvite(uuid);

        IslandData ownerIsland = manager.getIsland(ownerUuid);
        if (ownerIsland == null) {
            msg(player, "&cCette île n'existe plus.");
            return;
        }
        if (ownerIsland.isBanned(uuid)) {
            msg(player, "&cTu as été banni de cette île entre-temps.");
            return;
        }

        // Si le joueur a déjà une île (propriétaire ou coéquipier ailleurs), elle est
        // intégralement supprimée avant de rejoindre la nouvelle équipe : build ET
        // niveau (blocs cassés) sont perdus, rien n'est transféré à la nouvelle île.
        if (manager.isOwner(uuid)) {
            manager.removeIsland(uuid);
        } else {
            IslandData currentIsland = manager.getIsland(uuid);
            if (currentIsland != null) {
                manager.removeTeamMember(currentIsland, uuid);
            }
        }

        manager.addTeamMember(ownerIsland, uuid);
        // Un joueur qui rejoint l'équipe n'a plus besoin d'être dans la liste
        // "de confiance" (il a déjà toutes les permissions via son statut de
        // coéquipier) : on nettoie l'éventuelle entrée /ob trust restante.
        if (ownerIsland.isTrusted(uuid)) {
            ownerIsland.removeTrusted(uuid);
        }
        OfflinePlayer ownerOffline = plugin.getServer().getOfflinePlayer(ownerUuid);
        msg(player, "&a✔ Tu as rejoint l'île de &e" + ownerOffline.getName() + "&a !");

        Player ownerPlayer = plugin.getServer().getPlayer(ownerUuid);
        if (ownerPlayer != null) {
            msg(ownerPlayer, "&a✔ &e" + player.getName() + " &aa rejoint ton île !");
        }

        teleportOnTop(player, ownerIsland);
    }

    private void teamDeny(Player player) {
        UUID uuid = player.getUniqueId();
        UUID ownerUuid = manager.getPendingInviteOwner(uuid);
        if (ownerUuid == null) {
            msg(player, "&cTu n'as aucune invitation d'équipe en attente.");
            return;
        }
        manager.clearPendingInvite(uuid);
        msg(player, "&7Invitation refusée.");
        Player ownerPlayer = plugin.getServer().getPlayer(ownerUuid);
        if (ownerPlayer != null) {
            msg(ownerPlayer, "&c" + player.getName() + " &7a refusé ton invitation.");
        }
    }

    private void kickMember(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (!manager.isOwner(uuid)) {
            msg(player, "&cSeul le propriétaire d'une île peut exclure un coéquipier.");
            return;
        }
        if (args.length < 2) {
            msg(player, "&cUsage: &f/ob kick <pseudo>");
            return;
        }

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        IslandData island = manager.getIsland(uuid);
        if (!island.isMember(target.getUniqueId())) {
            msg(player, "&cCe joueur ne fait pas partie de ton équipe.");
            return;
        }

        manager.removeTeamMember(island, target.getUniqueId());
        msg(player, "&a✔ &e" + target.getName() + " &aa été exclu de ton île.");

        Player targetPlayer = target.getPlayer();
        if (targetPlayer != null) {
            msg(targetPlayer, "&cTu as été exclu de l'île de &f" + player.getName() + "&c. Utilise &f/ob start &cpour créer la tienne.");
            sendHomeIfInsideForeignIsland(targetPlayer);
        }
    }

    private void banPlayer(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (!manager.isOwner(uuid)) {
            msg(player, "&cSeul le propriétaire d'une île peut bannir un joueur.");
            return;
        }
        if (args.length < 2) {
            msg(player, "&cUsage: &f/ob ban <pseudo>");
            return;
        }

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(uuid)) {
            msg(player, "&cTu ne peux pas te bannir toi-même.");
            return;
        }
        if (isBanProtected(target)) {
            msg(player, "&c" + target.getName() + " &cne peut pas être banni (opérateur ou protégé).");
            return;
        }

        IslandData island = manager.getIsland(uuid);
        if (island.isMember(target.getUniqueId())) {
            manager.removeTeamMember(island, target.getUniqueId());
        }
        if (island.isTrusted(target.getUniqueId())) {
            island.removeTrusted(target.getUniqueId());
        }
        if (island.isBanned(target.getUniqueId())) {
            msg(player, "&e" + target.getName() + " &eest déjà banni de ton île.");
            return;
        }
        island.banPlayer(target.getUniqueId());
        msg(player, "&a✔ &e" + target.getName() + " &aest désormais banni de ton île (ne peut plus être invité, ni la visiter).");

        Player targetPlayer = target.getPlayer();
        if (targetPlayer != null) {
            msg(targetPlayer, "&cTu as été banni de l'île de &f" + player.getName() + "&c.");
            sendHomeIfInsideForeignIsland(targetPlayer);
        }
    }

    private void unbanPlayer(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (!manager.isOwner(uuid)) {
            msg(player, "&cSeul le propriétaire d'une île peut débannir un joueur.");
            return;
        }
        if (args.length < 2) {
            msg(player, "&cUsage: &f/ob unban <pseudo>");
            return;
        }

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        IslandData island = manager.getIsland(uuid);
        if (!island.isBanned(target.getUniqueId())) {
            msg(player, "&e" + target.getName() + " &en'est pas banni de ton île.");
            return;
        }
        island.unbanPlayer(target.getUniqueId());
        msg(player, "&a✔ &e" + target.getName() + " &an'est plus banni de ton île.");
    }

    /**
     * /ob trust <pseudo> : accorde à un joueur le rôle TRUST (voir /ob perms
     * pour régler les 36 permissions précises de ce rôle) sur l'île du
     * propriétaire, SANS en faire un coéquipier : contrairement à
     * /ob team invite, le joueur de confiance garde entièrement sa propre
     * île (elle n'est ni supprimée, ni fusionnée), et n'apparaît pas dans
     * /ob team list.
     *   /ob trust          -> liste les joueurs de confiance actuels
     *   /ob trust <pseudo> -> accorde le rôle TRUST à ce joueur
     */
    private void trustPlayer(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (!manager.isOwner(uuid)) {
            msg(player, "&cSeul le propriétaire d'une île peut faire confiance à un joueur.");
            return;
        }
        IslandData island = manager.getIsland(uuid);
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }

        if (args.length < 2) {
            if (island.getTrustedPlayers().isEmpty()) {
                msg(player, "&7Aucun joueur de confiance pour le moment.");
            } else {
                msg(player, "&6=== Joueurs de confiance (rôle TRUST) ===");
                for (UUID trustedUuid : island.getTrustedPlayers()) {
                    OfflinePlayer t = plugin.getServer().getOfflinePlayer(trustedUuid);
                    msg(player, "&7- &f" + t.getName());
                }
            }
            msg(player, "&7Utilise &f/ob trust <pseudo> &7pour faire confiance à quelqu'un : il garde sa propre île &8(contrairement à /ob team invite)&7.");
            msg(player, "&7Utilise &f/ob perms &7pour régler les permissions précises du rôle TRUST (et des autres rôles).");
            return;
        }

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        UUID targetUuid = target.getUniqueId();
        if (targetUuid.equals(uuid)) {
            msg(player, "&cTu ne peux pas te faire confiance à toi-même.");
            return;
        }
        if (target.getName() == null) {
            msg(player, "&cJoueur introuvable: &f" + args[1]);
            return;
        }
        if (island.isMember(targetUuid)) {
            msg(player, "&e" + target.getName() + " &eest déjà coéquipier de ton île, il a donc déjà toutes les permissions.");
            return;
        }
        if (island.isBanned(targetUuid)) {
            msg(player, "&cCe joueur est banni de ton île. Utilise &f/ob unban " + target.getName() + " &cd'abord.");
            return;
        }

        boolean wasAlreadyTrusted = island.isTrusted(targetUuid);
        island.addTrusted(targetUuid);
        msg(player, "&a✔ &e" + target.getName() + " &aest désormais un joueur de confiance (rôle TRUST) sur ton île "
                + "&7(il garde sa propre île). Utilise &f/ob perms &7pour régler les permissions de ce rôle.");
        Player targetOnline = target.getPlayer();
        if (targetOnline != null && !wasAlreadyTrusted) {
            msg(targetOnline, "&6" + player.getName() + " &et'a accordé sa confiance sur son île !");
            msg(targetOnline, "&7Utilise &f/is visit " + player.getName() + " &7pour la visiter (tu gardes ta propre île).");
        }
    }

    /** /ob untrust <pseudo> : retire un joueur de la liste de confiance de l'île. */
    private void untrustPlayer(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (!manager.isOwner(uuid)) {
            msg(player, "&cSeul le propriétaire d'une île peut retirer sa confiance à un joueur.");
            return;
        }
        if (args.length < 2) {
            msg(player, "&cUsage: &f/ob untrust <pseudo>");
            return;
        }
        IslandData island = manager.getIsland(uuid);
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        UUID targetUuid = target.getUniqueId();
        if (!island.isTrusted(targetUuid)) {
            msg(player, "&e" + target.getName() + " &en'est pas dans tes joueurs de confiance.");
            return;
        }

        island.removeTrusted(targetUuid);
        msg(player, "&a✔ &e" + target.getName() + " &an'est plus un joueur de confiance sur ton île.");
        Player targetOnline = target.getPlayer();
        if (targetOnline != null) {
            msg(targetOnline, "&c" + player.getName() + " &7ne te fait plus confiance sur son île.");
        }
    }

    /**
     * /ob menu : ouvre un GUI listant les joueurs connus (en ligne,
     * propriétaires d'île, coéquipiers/joueurs de confiance de ta propre
     * île) pour agir dessus au clic gauche/droit selon la catégorie
     * choisie en haut ("Visite" ou "Sanction"), sans avoir à taper de
     * commande — voir OBMenuGUI et GuiListener#handleOBMenuClick pour le
     * détail des actions déclenchées (elles réutilisent les mêmes
     * sous-commandes /ob que d'habitude, donc les mêmes permissions).
     */
    private void openMenu(Player player) {
        fr.tuto.oneblock.gui.OBMenuGUI.open(plugin, player, fr.tuto.oneblock.gui.OBMenuGuiHolder.Category.VISITE);
    }

    private void openPermissions(Player player) {
        UUID uuid = player.getUniqueId();
        if (!manager.isOwner(uuid)) {
            msg(player, "&cSeul le propriétaire d'une île peut gérer les permissions de l'équipe.");
            return;
        }
        IslandData island = manager.getIsland(uuid);
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }
        fr.tuto.oneblock.gui.RolePermissionsGUI.open(plugin, player, island);
    }

    private void leaveTeam(Player player, String[] args) {
        UUID uuid = player.getUniqueId();

        if (manager.isOwner(uuid)) {
            msg(player, "&cTu es propriétaire de ton île, tu ne peux pas la quitter. Utilise &f/ob reset &cpour la réinitialiser.");
            return;
        }
        IslandData island = manager.getIsland(uuid);
        if (island == null || !island.isMember(uuid)) {
            msg(player, "&cTu ne fais partie d'aucune équipe.");
            return;
        }

        boolean confirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
        if (!confirm) {
            msg(player, "&c&l⚠ Attention ! &cQuitter cette île va &ete recréer une île OneBlock toute neuve (niveau 1) &c: tu perdras l'accès à celle-ci et à ses constructions.");
            msg(player, "&7Tape &f/ob leave confirm &7dans les 30 secondes pour confirmer.");
            pendingLeaveConfirm.put(uuid, System.currentTimeMillis() + LEAVE_CONFIRM_WINDOW_MS);
            return;
        }

        Long expiry = pendingLeaveConfirm.get(uuid);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            msg(player, "&cConfirmation expirée, retape &f/ob leave&c.");
            return;
        }
        pendingLeaveConfirm.remove(uuid);

        OfflinePlayer ownerOffline = plugin.getServer().getOfflinePlayer(island.getOwner());
        manager.removeTeamMember(island, uuid);
        msg(player, "&a✔ Tu as quitté l'île de &e" + ownerOffline.getName() + "&a.");

        Player ownerPlayer = plugin.getServer().getPlayer(island.getOwner());
        if (ownerPlayer != null) {
            msg(ownerPlayer, "&e" + player.getName() + " &7a quitté ton île.");
        }

        IslandData newIsland = manager.createIsland(uuid, player.getName());
        teleportOnTop(player, newIsland);
        msg(player, "&a✔ Une nouvelle île OneBlock vierge a été créée pour toi.");
    }

    /** Renvoie le joueur au spawn s'il se trouve physiquement dans le monde d'une île qu'il vient de perdre (kick/ban). */
    private void sendHomeIfInsideForeignIsland(Player player) {
        Location spawn = manager.getServerSpawnLocation();
        if (spawn == null) return;
        if (!manager.hasIsland(player.getUniqueId())
                || manager.getIsland(player.getUniqueId()).getBlockLocation().getWorld() != player.getWorld()) {
            // Le joueur n'a plus d'île, ou n'est plus dans le monde de son ancienne île :
            // dans les deux cas on ne le laisse pas coincé dans un monde qui n'est plus le sien.
            player.setWorldBorder(null);
            manager.stopBorderParticles(player);
            player.teleport(spawn);
        }
    }

    private void visitIsland(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&cUsage: &f/ob visit <pseudo>");
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
        UUID targetUuid = target.getUniqueId();

        IslandData targetIsland = manager.getIsland(targetUuid);
        if (targetIsland == null) {
            msg(player, "&cAucune île OneBlock trouvée pour &f" + targetName + "&c.");
            return;
        }

        // Si le pseudo visé fait partie de la même île que le joueur (lui-même ou
        // un de ses coéquipiers), on le ramène simplement chez lui plutôt que de
        // le traiter comme un "visiteur" de sa propre île.
        IslandData myIsland = manager.getIsland(player.getUniqueId());
        if (myIsland == targetIsland) {
            msg(player, "&eC'est déjà ta propre île ! Utilise &f/ob tp &eou &f/is&e.");
            teleportBack(player);
            return;
        }

        if (targetIsland.isBanned(player.getUniqueId())) {
            msg(player, "&cTu es banni de cette île, tu ne peux pas la visiter.");
            return;
        }

        // Île verrouillée par son propriétaire (/ob lock) : plus personne
        // d'autre que le propriétaire/les coéquipiers (ou un op) ne peut la
        // visiter, quel que soit le réglage habituel des visites.
        if (targetIsland.isLocked() && !targetIsland.isOwnerMemberOrTrusted(player.getUniqueId()) && !player.isOp()) {
            msg(player, "&cCette île est verrouillée par son propriétaire, tu ne peux pas la visiter.");
            return;
        }

        teleportOnTop(player, targetIsland);
        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(targetIsland.getOwner());
        String ownerName = owner.getName() != null ? owner.getName() : targetName;
        msg(player, "&a✔ Tu visites l'île de &e" + ownerName + "&a.");
    }

    /**
     * /ob lock (ou /is lock) : verrouille/déverrouille l'île du joueur.
     * Une île verrouillée ne peut plus être visitée par personne d'autre que
     * le propriétaire, ses coéquipiers, ou un op (voir visitIsland ci-dessus
     * et IslandSettingsListener pour les protections de construction/casse/
     * conteneurs déjà appliquées à tout visiteur, membre ou non). Comme pour
     * /ob permissions, seul le propriétaire de l'île peut la verrouiller.
     */
    private void setLocked(Player player, boolean lock) {
        IslandData island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            msg(player, "&cTu n'as pas encore d'île. Utilise &f/ob start&c.");
            return;
        }
        if (!island.getOwner().equals(player.getUniqueId())) {
            msg(player, "&cSeul le propriétaire de l'île peut la verrouiller/déverrouiller.");
            return;
        }

        if (island.isLocked() == lock) {
            msg(player, lock
                    ? "&eTon île est déjà verrouillée."
                    : "&eTon île n'est pas verrouillée.");
            return;
        }

        island.setLocked(lock);

        if (lock) {
            msg(player, "&a✔ Île verrouillée : plus aucun visiteur ne peut y entrer (&f/ob visit&a bloqué pour tout le monde sauf tes coéquipiers).");
            kickCurrentVisitors(island);
        } else {
            msg(player, "&a✔ Île déverrouillée : les visiteurs peuvent à nouveau la visiter via &f/ob visit&a.");
        }
    }

    /**
     * Expulse immédiatement, vers le spawn du serveur, tout joueur déjà
     * présent sur l'île au moment où elle vient d'être verrouillée (et qui
     * n'est ni le propriétaire, ni un coéquipier, ni un op) : sinon un
     * visiteur déjà sur place au moment du /ob lock y resterait bloqué
     * jusqu'à sa prochaine déconnexion/téléportation volontaire.
     */
    private void kickCurrentVisitors(IslandData island) {
        var world = island.getBlockLocation().getWorld();
        if (world == null) return;

        Location spawnLoc = manager.getServerSpawnLocation();
        if (spawnLoc == null) return; // pas de spawn configuré : on ne peut pas expulser proprement

        for (Player online : new ArrayList<>(world.getPlayers())) {
            if (online.isOp()) continue;
            if (island.isOwnerMemberOrTrusted(online.getUniqueId())) continue;

            online.setWorldBorder(null);
            plugin.getManager().stopBorderParticles(online);
            online.teleport(spawnLoc);
            msg(online, "&cCette île vient d'être verrouillée par son propriétaire, tu as été renvoyé au spawn.");
        }
    }

    private void teleportOnTop(Player player, IslandData island) {
        Location loc = island.getBlockLocation().clone().add(0.5, 1.0, 0.5);
        player.teleport(loc);

        // La bordure doit être (ré)appliquée APRÈS le téléport, jamais avant.
        // Si elle est envoyée avant que le joueur change de monde (ex: venant
        // de /spawn, situé dans un autre monde), le changement de monde
        // provoqué par teleport() réinitialise la bordure au monde par
        // défaut et le client ne reçoit jamais celle de l'île -> bordure
        // invisible un coup sur deux. On la renvoie en plus au tick suivant
        // pour être sûr que le paquet de bordure parte après celui de
        // téléportation.
        manager.applyBorder(player, island);
        plugin.getManager().applyTimeLock(player, island);
        // Comme pour la bordure ci-dessus : si le joueur change de monde
        // (ex: venant du spawn du serveur), le teleport() qui suit provoque
        // un changement de monde côté client qui réinitialise son horloge
        // perçue -> le paquet "jour éternel" envoyé AVANT/PENDANT le
        // changement de monde est écrasé et le joueur revoit le cycle
        // jour/nuit normal malgré le réglage activé. On le renvoie donc
        // aussi au tick suivant pour être sûr qu'il arrive après celui du
        // changement de monde.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                manager.applyBorder(player, island);
                plugin.getManager().applyTimeLock(player, island);
            }
        });

        boolean fly = island.getSetting("fly", false);
        player.setAllowFlight(fly);
        if (!fly && player.isFlying()) {
            player.setFlying(false);
        }

        plugin.getBossBarManager().update(player, island);
    }

    private void sendHelp(Player player) {
        msg(player, "&6=== Commandes OneBlock ===");
        msg(player, "&f/ob start &7- Crée ton île OneBlock");
        msg(player, "&f/ob tp &7&f(ou /is) &7- Retourne sur ton île");
        msg(player, "&f/ob reset &7- Réinitialise entièrement ton île (terrain, niveau, upgrades, renaissances)");
        msg(player, "&f/ob niveau &7- Affiche ta progression");
        msg(player, "&f/ob renaissance &7&f(ou /rebirth) &7- Repars niveau 1 depuis Fin du Monde (garde tes renaissances)");
        msg(player, "&f/ob agrandir &7- Agrandit ta bordure d'île (coûte de l'argent)");
        msg(player, "&f/ob visit <pseudo> &7&f(ou /is visit <pseudo>) &7- Visite l'île d'un autre joueur");
        msg(player, "&f/ob settings &7- Ouvre le GUI des paramètres de ton île (mobs, pvp...)");
        msg(player, "&6--- Équipe ---");
        msg(player, "&f/ob team &7- Affiche ton équipe &f(ou /ob team invite <pseudo>, accept, deny)");
        msg(player, "&f/ob chat &7- Active/désactive le chat d'équipe &f(ou /ob chat <message> &7pour un seul message)");
        msg(player, "&f/ob kick <pseudo> &7- Exclut un coéquipier de ton île &8(propriétaire)");
        msg(player, "&f/ob ban <pseudo> &7- Bannit un joueur de ton île &8(propriétaire)");
        msg(player, "&f/ob unban <pseudo> &7- Débannit un joueur &8(propriétaire)");
        msg(player, "&f/ob permissions &7- Gère les droits détaillés (construire/casser/coffres/banque/interactions) de tes coéquipiers et joueurs de confiance, et les accès visiteurs &8(propriétaire)");
        msg(player, "&f/ob leave &7- Quitte l'équipe que tu as rejointe et recrée ta propre île");
        msg(player, "&f/ob lock &7&f(ou /is lock) &7- Verrouille ton île : plus aucun visiteur ne peut y entrer &8(propriétaire)");
        msg(player, "&f/ob unlock &7&f(ou /is unlock) &7- Déverrouille ton île &8(propriétaire)");
        msg(player, "&f/ob trust <pseudo> &7- Donne à un joueur le droit de construire/casser/utiliser les coffres, &esans qu'il perde sa propre île &8(propriétaire)");
        msg(player, "&f/ob trust <pseudo> <build|break|containers|bank|interact> <true|false> &7- Choisit précisément ses permissions &8(propriétaire)");
        msg(player, "&f/ob untrust <pseudo> &7- Retire un joueur de la liste de confiance &8(propriétaire)");
        msg(player, "&f/ob menu &7- GUI rapide pour visiter/faire confiance/bannir un joueur en un clic");
        msg(player, "&f/bankis &7- Gère la caisse d'île (dépôt/retrait, &f/bankis objets&7 pour le coffre partagé)");
        msg(player, "&f/token &7- Boutique à tokens (ex: débloquer le vol sur l'île)");
        msg(player, "&f/upgrades &7- Améliorations d'île achetables avec ton argent");
        msg(player, "&f/rebirthshop &7- Boutique de renaissance (achetable avec tes points de renaissance)");
        msg(player, "&f/top &7- Classements (blocs, mobs, argent, caisse)");
        msg(player, "&8Plugin par &7LeGameurPSN_YTB &8| Contributeurs: &7Rz_Nelix_MC, Evernight_7th");
    }

    private void msg(Player player, String text) {
        Component c = legacy.deserialize(text);
        player.sendMessage(c);
    }

    // ==================== AUTO-COMPLÉTION ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        Player player = (Player) sender;

        // /is et /island n'ont pas de sous-commandes à compléter, sauf "visit"/"lock"/"unlock"
        if (alias.equalsIgnoreCase("is") || alias.equalsIgnoreCase("island")) {
            if (args.length == 1) {
                return filterStartsWith(Arrays.asList("visit", "visite", "lock", "unlock"), args[0]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("visit") || args[0].equalsIgnoreCase("visite"))) {
                return completeVisitTargets(args[1]);
            }
            return List.of();
        }

        // /rebirth et /renaissance n'ont pas d'argument à compléter
        if (alias.equalsIgnoreCase("rebirth") || alias.equalsIgnoreCase("renaissance")) {
            return List.of();
        }

        // /ob <terme> -> on propose tous les termes de commande disponibles
        if (args.length == 1) {
            return filterStartsWith(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if (sub.equals("settings") || sub.equals("parametres")) {
                List<String> keys = manager.getDefaultSettings().keySet().stream()
                        .filter(k -> !k.equals("keep-inventory"))
                        .sorted()
                        .collect(Collectors.toList());
                return filterStartsWith(keys, args[1]);
            }
            if (sub.equals("visit") || sub.equals("visite")) {
                return completeVisitTargets(args[1]);
            }
            if (sub.equals("team")) {
                return filterStartsWith(Arrays.asList("invite", "accept", "deny", "list"), args[1]);
            }
            if (sub.equals("kick")) {
                return completeOwnMembers(player, args[1]);
            }
            if (sub.equals("ban") || sub.equals("unban")) {
                return completeVisitTargets(args[1]);
            }
            if (sub.equals("trust") || sub.equals("untrust")) {
                return completeVisitTargets(args[1]);
            }
            if (sub.equals("leave")) {
                return filterStartsWith(List.of("confirm"), args[1]);
            }
        }

        if (args.length == 3 && (sub.equals("settings") || sub.equals("parametres"))) {
            return filterStartsWith(Arrays.asList("true", "false"), args[2]);
        }
        if (args.length == 3 && sub.equals("team") && args[1].equalsIgnoreCase("invite")) {
            return completePlayerNames(args[2]);
        }


        return List.of();
    }

    private List<String> filterStartsWith(List<String> options, String current) {
        String lower = current.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private List<String> completePlayerNames(String current) {
        String lower = current.toLowerCase();
        List<String> names = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(lower)) {
                names.add(p.getName());
            }
        }
        return names;
    }

    /**
     * Comme completePlayerNames, mais inclut en plus tous les propriétaires
     * d'île connus du plugin (même hors-ligne), utilisé pour /ob visit et
     * /is visit afin de pouvoir viser n'importe quel joueur ayant déjà une île,
     * qu'il soit connecté ou non.
     */
    private List<String> completeVisitTargets(String current) {
        String lower = current.toLowerCase();
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(lower)) {
                names.add(p.getName());
            }
        }

        for (IslandData island : manager.getAllIslands()) {
            UUID ownerUuid = island.getOwner();
            if (ownerUuid == null) continue;
            String ownerName = plugin.getServer().getOfflinePlayer(ownerUuid).getName();
            if (ownerName != null && ownerName.toLowerCase().startsWith(lower)) {
                names.add(ownerName);
            }
        }

        return new ArrayList<>(names);
    }

    /**
     * Autocomplétion pour /ob kick : uniquement les coéquipiers (membres) de
     * ta propre île, en ligne ou hors-ligne (contrairement à /ob ban ou
     * /ob trust qui peuvent viser n'importe quel joueur connu du serveur).
     */
    private List<String> completeOwnMembers(Player player, String current) {
        String lower = current.toLowerCase();
        List<String> names = new ArrayList<>();
        IslandData island = manager.getIsland(player.getUniqueId());
        if (island == null) return names;

        for (UUID memberUuid : island.getMembers()) {
            String memberName = plugin.getServer().getOfflinePlayer(memberUuid).getName();
            if (memberName != null && memberName.toLowerCase().startsWith(lower)) {
                names.add(memberName);
            }
        }
        return names;
    }

    /**
     * Un joueur op, ou possédant la permission oneblock.ban.bypass, ne peut
     * pas être banni d'une île avec /ob ban. La vérification fonctionne même
     * hors-ligne grâce à LuckPerms (si installé) ; sinon, seul le statut op
     * (persistant) peut être vérifié pour un joueur déconnecté.
     */
    private boolean isBanProtected(OfflinePlayer target) {
        if (target.isOp()) return true;

        Player online = target.getPlayer();
        if (online != null) {
            return online.hasPermission("oneblock.ban.bypass");
        }

        fr.tuto.oneblock.managers.LuckPermsManager lp = plugin.getLuckPermsManager();
        if (lp != null && lp.isEnabled()) {
            return lp.hasPermission(target, "oneblock.ban.bypass");
        }

        return false;
    }
}
