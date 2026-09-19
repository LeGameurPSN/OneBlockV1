package fr.tuto.oneblock.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur utilisé pour reconnaître le GUI des classements (/top) dans le
 * listener, et retenir la catégorie actuellement affichée à ce joueur
 * (tokens, mobs tués, argent perso, caisse d'île, points de renaissance ou
 * renaissances totales).
 */
public class TopGuiHolder implements InventoryHolder {

    private final Player owner;
    private Inventory inventory;
    private String category;

    public TopGuiHolder(Player owner, String category) {
        this.owner = owner;
        this.category = category;
    }

    public Player getOwner() {
        return owner;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
