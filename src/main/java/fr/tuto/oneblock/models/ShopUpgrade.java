package fr.tuto.oneblock.models;

import org.bukkit.Material;

import java.util.List;

/**
 * Un article achetable dans une boutique (/upgrades ou /rebirthshop).
 *
 * Chargé dynamiquement depuis config.yml/rebirth.yml (sections "upgrades"
 * et "rebirth-shop.items"), ce qui permet d'ajouter facilement de
 * nouveaux articles sans toucher au code, tant qu'un effet leur est
 * associé quelque part (voir OneBlockManager / GuiListener).
 *
 * Deux types d'articles :
 *  - À niveaux (repeatable=false, comportement par défaut) : le coût du
 *    niveau suivant est {@code baseCost * costMultiplier ^ niveauActuel},
 *    et l'effet de bonus (pourcentage, chance...) est
 *    {@code niveauActuel * bonusPerLevel}.
 *  - Répétables (repeatable=true) : pas de palier ni de coût croissant,
 *    prix fixe (baseCost) et effet immédiat à chaque achat. Dans ce cas,
 *    bonusPerLevel sert de montant accordé à chaque achat (ex: argent
 *    donné), voir GuiListener#handleRebirthShopClick.
 */
public record ShopUpgrade(String key, String name, List<String> description, Material icon,
                           int maxLevel, double baseCost, double costMultiplier, double bonusPerLevel,
                           boolean repeatable) {

    /** Coût pour passer du niveau {@code currentLevel} au niveau suivant (ou coût fixe si répétable). */
    public double costForLevel(int currentLevel) {
        if (repeatable) return baseCost;
        return baseCost * Math.pow(costMultiplier, currentLevel);
    }

    /** Bonus total (en %, ou en unité selon l'amélioration) au niveau donné. Sans effet pour un article répétable. */
    public double bonusAtLevel(int level) {
        return level * bonusPerLevel;
    }
}
