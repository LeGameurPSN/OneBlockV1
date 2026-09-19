package fr.tuto.oneblock.models;

import java.util.List;
import java.util.Random;

public class OneBlockLevel {

    private final int id;
    private final String name;
    private final int minBlocks;
    private final int maxBlocks; // -1 = illimité
    private final List<LootItem> loot;
    private final int totalWeight;

    private static final Random RANDOM = new Random();

    public OneBlockLevel(int id, String name, int minBlocks, int maxBlocks, List<LootItem> loot) {
        this.id = id;
        this.name = name;
        this.minBlocks = minBlocks;
        this.maxBlocks = maxBlocks;
        this.loot = loot;
        int total = 0;
        for (LootItem item : loot) total += item.getWeight();
        this.totalWeight = total;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMinBlocks() {
        return minBlocks;
    }

    public int getMaxBlocks() {
        return maxBlocks;
    }

    public boolean matches(int brokenCount) {
        if (brokenCount < minBlocks) return false;
        return maxBlocks == -1 || brokenCount <= maxBlocks;
    }

    /**
     * Tire un objet de la loot-table de ce niveau selon son poids (weighted random).
     */
    public LootItem rollLoot() {
        if (loot.isEmpty() || totalWeight <= 0) return null;
        int roll = RANDOM.nextInt(totalWeight);
        int cumulative = 0;
        for (LootItem item : loot) {
            cumulative += item.getWeight();
            if (roll < cumulative) {
                return item;
            }
        }
        return loot.get(loot.size() - 1);
    }
}
