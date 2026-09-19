package fr.tuto.oneblock.placeholders;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import fr.tuto.oneblock.models.OneBlockLevel;
import fr.tuto.oneblock.models.ShopUpgrade;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Expansion PlaceholderAPI pour OneBlock.
 *
 * Nécessite le plugin PlaceholderAPI installé sur le serveur (softdepend,
 * voir plugin.yml). Enregistrée uniquement si PlaceholderAPI est présent
 * (voir OneBlockPlugin#onEnable).
 *
 * Tous les placeholders sont préfixés par "oneblock_", par ex. :
 *   %oneblock_level%
 *   %oneblock_bank_formatted%
 *   %oneblock_upgrade_bank-boost%
 */
public class OneBlockExpansion extends PlaceholderExpansion {

    private final OneBlockPlugin plugin;

    public OneBlockExpansion(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "oneblock";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LeGameurPSN_YTB";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * true = l'expansion reste enregistrée après un /papi reload, sans
     * avoir besoin de relancer OneBlock.
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        OneBlockManager manager = plugin.getManager();
        String key = params.toLowerCase();
        UUID uuid = player.getUniqueId();
        IslandData island = manager.getIsland(uuid);

        // %oneblock_has_island%
        if (key.equals("has_island")) {
            return String.valueOf(island != null);
        }

        // Compte perso Vault, dispo même sans île
        if (key.equals("balance") || key.equals("money")) {
            return String.valueOf(plugin.getVaultManager().getBalance(player));
        }
        if (key.equals("balance_formatted") || key.equals("money_formatted")) {
            return plugin.getVaultManager().format(plugin.getVaultManager().getBalance(player));
        }
        if (key.equals("border_max")) {
            int max = manager.getBorderMaxSize();
            return max == -1 ? "illimité" : String.valueOf(max);
        }
        if (key.equals("rebirth_required_level")) {
            return String.valueOf(manager.getRebirthRequiredLevel());
        }
        if (key.equals("rebirth_enabled")) {
            return String.valueOf(manager.isRebirthEnabled());
        }
        // %oneblock_rebirthtop% -> classement (rang) du joueur dans le top renaissances
        if (key.equals("rebirthtop") || key.equals("rebirth_rank")) {
            return String.valueOf(getRebirthRank(manager, uuid));
        }

        // %oneblock_rank_<catégorie>% -> rang (1 = premier) du joueur dans ce
        // classement (token, mob, bloc, argent, banque, rebirthpoints, rebirths).
        // Pratique pour trier des groupes dans un plugin de tab-list.
        if (key.startsWith("rank_")) {
            String category = key.substring("rank_".length());
            return String.valueOf(getRank(plugin, category, uuid));
        }

        // %oneblock_top_<catégorie>_<position>_name% -> nom du joueur classé
        // à cette position (1 = premier). "------" si aucune donnée à ce rang.
        // %oneblock_top_<catégorie>_<position>_value% -> sa valeur formatée.
        // %oneblock_top_<catégorie>_<position>% -> alias de "_name".
        // %oneblock_top_<catégorie>_ma_place% -> place (rang) du joueur qui
        // regarde le placeholder dans ce classement. "-" s'il n'est pas classé.
        // Ex: %oneblock_top_argent_1_name%, %oneblock_top_banque_3_value%,
        //     %oneblock_top_argent_ma_place%, %oneblock_top_token_ma_place%
        if (key.startsWith("top_")) {
            return resolveTopPlaceholder(plugin, player, key.substring("top_".length()));
        }

        if (island == null) {
            // Pas encore d'île : valeurs par défaut plutôt qu'un placeholder vide
            return switch (key) {
                case "level", "level_id" -> "0";
                case "level_name" -> "-";
                case "broken", "broken_count" -> "0";
                case "level_min_blocks", "level_max_blocks" -> "-";
                case "level_progress" -> "-";
                case "next_level_name", "next_level_blocks" -> "-";
                case "rebirths" -> "0";
                case "rebirth_points" -> "0";
                case "bank", "bank_balance" -> "0";
                case "bank_formatted" -> plugin.getVaultManager().format(0);
                case "border", "border_size" -> "0";
                case "tokens" -> "0";
                case "fly", "fly_unlocked" -> "false";
                case "mobs_killed" -> "0";
                case "percentage" -> "0";
                case "bar" -> buildBar(0);
                case "rebirthpoints" -> "0";
                default -> "";
            };
        }

        OneBlockLevel currentLevel = manager.getLevelForCount(island.getBrokenCount());

        switch (key) {
            case "level":
            case "level_id":
                return currentLevel != null ? String.valueOf(currentLevel.getId()) : "0";
            case "level_name":
                return currentLevel != null ? currentLevel.getName() : "-";
            case "broken":
            case "broken_count":
                return String.valueOf(island.getBrokenCount());
            case "level_min_blocks":
                return currentLevel != null ? String.valueOf(currentLevel.getMinBlocks()) : "-";
            case "level_max_blocks":
                return currentLevel != null && currentLevel.getMaxBlocks() != -1
                        ? String.valueOf(currentLevel.getMaxBlocks()) : "illimité";
            case "level_progress": {
                if (currentLevel == null || currentLevel.getMaxBlocks() == -1) return "-";
                int done = Math.max(0, island.getBrokenCount() - currentLevel.getMinBlocks());
                int size = currentLevel.getMaxBlocks() - currentLevel.getMinBlocks() + 1;
                return done + "/" + size;
            }
            case "next_level_name": {
                if (currentLevel == null) return "-";
                OneBlockLevel next = manager.getLevelById(currentLevel.getId() + 1);
                return next != null ? next.getName() : "-";
            }
            case "next_level_blocks": {
                if (currentLevel == null) return "-";
                OneBlockLevel next = manager.getLevelById(currentLevel.getId() + 1);
                return next != null ? String.valueOf(next.getMinBlocks()) : "-";
            }
            case "rebirths":
                return String.valueOf(island.getRebirths());
            case "rebirth_points":
                return String.valueOf(island.getRebirthPoints());
            case "bank":
            case "bank_balance":
                return String.valueOf(island.getBankBalance());
            case "bank_formatted":
                return plugin.getVaultManager().format(island.getBankBalance());
            case "border":
            case "border_size":
                return String.valueOf(island.getBorderSize());
            case "tokens":
                // Solde PERSONNEL du joueur qui demande le placeholder.
                return String.valueOf(island.getTokens(uuid));
            case "tokens_team":
                // Total combiné de toute l'équipe (propriétaire + coéquipiers).
                return String.valueOf(island.getTeamTokensTotal());
            case "fly":
            case "fly_unlocked":
                return String.valueOf(island.isFlyUnlocked());
            case "mobs_killed":
                return String.valueOf(island.getMobsKilled());
            case "rebirthpoints":
                // alias pratique de rebirth_points
                return String.valueOf(island.getRebirthPoints());
            case "percentage": {
                // avancement (en %) dans le niveau actuel
                if (currentLevel == null || currentLevel.getMaxBlocks() == -1) return "100";
                int done = Math.max(0, island.getBrokenCount() - currentLevel.getMinBlocks());
                int size = currentLevel.getMaxBlocks() - currentLevel.getMinBlocks() + 1;
                int percent = size <= 0 ? 100 : Math.min(100, (int) Math.round((done * 100.0) / size));
                return String.valueOf(percent);
            }
            case "bar": {
                // barre de progression textuelle (ex: ▰▰▰▰▰▱▱▱▱▱) basée sur le % ci-dessus
                int percent;
                if (currentLevel == null || currentLevel.getMaxBlocks() == -1) {
                    percent = 100;
                } else {
                    int done = Math.max(0, island.getBrokenCount() - currentLevel.getMinBlocks());
                    int size = currentLevel.getMaxBlocks() - currentLevel.getMinBlocks() + 1;
                    percent = size <= 0 ? 100 : Math.min(100, (int) Math.round((done * 100.0) / size));
                }
                return buildBar(percent);
            }
            default:
                break;
        }

        // %oneblock_upgrade_<clé>% -> niveau actuel d'une amélioration /upgrades (ex: bank-boost)
        if (key.startsWith("upgrade_")) {
            String upgradeKey = params.substring("upgrade_".length());
            return String.valueOf(island.getUpgradeLevel(upgradeKey));
        }

        // %oneblock_upgrademax_<clé>% -> niveau max de cette amélioration /upgrades
        if (key.startsWith("upgrademax_")) {
            String upgradeKey = params.substring("upgrademax_".length());
            ShopUpgrade def = manager.getUpgrades().get(upgradeKey);
            return def != null ? String.valueOf(def.maxLevel()) : "0";
        }

        // %oneblock_rebirthshop_<clé>% -> niveau actuel d'une amélioration /rebirthshop
        if (key.startsWith("rebirthshop_")) {
            String upgradeKey = params.substring("rebirthshop_".length());
            return String.valueOf(island.getRebirthShopLevel(upgradeKey));
        }

        // %oneblock_rebirthshopmax_<clé>% -> niveau max de cette amélioration /rebirthshop
        if (key.startsWith("rebirthshopmax_")) {
            String upgradeKey = params.substring("rebirthshopmax_".length());
            ShopUpgrade def = manager.getRebirthShopItems().get(upgradeKey);
            return def != null ? String.valueOf(def.maxLevel()) : "0";
        }

        return null; // placeholder inconnu -> PlaceholderAPI laisse le texte tel quel
    }

    /**
     * Construit une barre de progression textuelle sur 10 crans à partir
     * d'un pourcentage (0-100), ex: "&a▰▰▰▰▰&7▱▱▱▱▱".
     */
    private String buildBar(int percent) {
        int size = 10;
        int filled = Math.max(0, Math.min(size, Math.round(percent * size / 100f)));
        StringBuilder bar = new StringBuilder();
        bar.append("&a");
        bar.append("▰".repeat(filled));
        bar.append("&7");
        bar.append("▱".repeat(size - filled));
        return bar.toString();
    }

    /**
     * Rang (1 = premier) du joueur dans le classement des renaissances,
     * toutes îles confondues. Retourne -1 si le joueur n'a pas d'île.
     */
    private int getRebirthRank(OneBlockManager manager, UUID uuid) {
        IslandData target = manager.getIsland(uuid);
        if (target == null) return -1;

        java.util.List<IslandData> islands = new java.util.ArrayList<>(manager.getAllIslands());
        islands.sort((a, b) -> Integer.compare(b.getRebirths(), a.getRebirths()));

        for (int i = 0; i < islands.size(); i++) {
            if (islands.get(i).getOwner().equals(uuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Rang (1 = premier) du joueur dans le classement d'une catégorie
     * quelconque parmi celles gérées par /top (token, mob, bloc, argent, banque,
     * rebirthpoints, rebirths). Retourne -1 si le joueur n'est pas classé
     * (pas d'île, ou catégorie inconnue).
     */
    private int getRank(OneBlockPlugin plugin, String category, UUID uuid) {
        java.util.List<fr.tuto.oneblock.gui.TopGUI.Entry> entries =
                fr.tuto.oneblock.gui.TopGUI.buildEntries(plugin, category);
        for (int i = 0; i < entries.size(); i++) {
            if (uuid.equals(entries.get(i).player().getUniqueId())) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Résout un placeholder "%oneblock_top_<reste>%" pour afficher le top
     * d'une catégorie (utile dans une tab-list / scoreboard). Formats
     * acceptés (reste, après avoir retiré le préfixe "top_") :
     *   "<catégorie>_<position>"        -> nom du joueur (alias de _name)
     *   "<catégorie>_<position>_name"   -> nom du joueur
     *   "<catégorie>_<position>_value"  -> valeur formatée
     *   "<catégorie>_ma_place"          -> place (rang) du joueur qui
     *                                       consulte le placeholder
     * "------" si aucun joueur n'occupe ce rang ("-" pour "ma_place" si le
     * joueur n'est pas classé).
     */
    private String resolveTopPlaceholder(OneBlockPlugin plugin, Player viewer, String rest) {
        boolean wantValue = false;
        if (rest.endsWith("_name")) {
            rest = rest.substring(0, rest.length() - "_name".length());
        } else if (rest.endsWith("_value")) {
            rest = rest.substring(0, rest.length() - "_value".length());
            wantValue = true;
        }

        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore == -1) return "------";

        String category = rest.substring(0, lastUnderscore);
        String posRaw = rest.substring(lastUnderscore + 1);

        // %oneblock_top_<catégorie>_ma_place% -> rang du joueur qui regarde
        // ("ma_place" utilise un "_" donc "place" est le dernier segment,
        // on vérifie donc aussi ce cas juste avant : "<catégorie>_ma_place").
        if (posRaw.equals("place") && category.endsWith("_ma")) {
            String realCategory = category.substring(0, category.length() - "_ma".length());
            int rank = getRank(plugin, realCategory, viewer.getUniqueId());
            return rank == -1 ? "-" : String.valueOf(rank);
        }

        int position;
        try {
            position = Integer.parseInt(posRaw);
        } catch (NumberFormatException e) {
            return "------";
        }
        if (position < 1) return "------";

        java.util.List<fr.tuto.oneblock.gui.TopGUI.Entry> entries =
                fr.tuto.oneblock.gui.TopGUI.buildEntries(plugin, category);
        if (position > entries.size()) {
            return "------";
        }

        fr.tuto.oneblock.gui.TopGUI.Entry entry = entries.get(position - 1);
        if (wantValue) {
            return entry.formatted();
        }
        // Nom(s) de toute l'équipe (chef + coéquipiers), pas seulement le chef.
        return entry.teamNames();
    }
}
