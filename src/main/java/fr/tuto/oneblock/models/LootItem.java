package fr.tuto.oneblock.models;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Représente une entrée possible dans la loot-table d'un niveau.
 * Peut être :
 *  - un bloc classique (ex: STONE, DIAMOND_ORE...)
 *  - un spawner (material == SPAWNER + entity défini)
 */
public class LootItem {

    private final Material material;
    private final int weight;
    private final EntityType entity; // uniquement pour les spawners

    public LootItem(Material material, int weight, EntityType entity) {
        this.material = material;
        this.weight = weight;
        this.entity = entity;
    }

    public Material getMaterial() {
        return material;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isSpawner() {
        return material == Material.SPAWNER && entity != null;
    }

    public EntityType getEntity() {
        return entity;
    }
}
