package fr.tuto.oneblock.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur utilisé pour reconnaître de façon fiable l'inventaire du GUI
 * de paramètres dans le listener (au lieu de comparer des titres).
 */
public class OneBlockGuiHolder implements InventoryHolder {

    private final Player owner;
    private Inventory inventory;

    public OneBlockGuiHolder(Player owner) {
        this.owner = owner;
    }

    public Player getOwner() {
        return owner;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
