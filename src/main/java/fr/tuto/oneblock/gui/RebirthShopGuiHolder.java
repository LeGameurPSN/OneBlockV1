package fr.tuto.oneblock.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur utilisé pour reconnaître de façon fiable l'inventaire de la
 * boutique de renaissance (/rebirthshop) dans le listener.
 */
public class RebirthShopGuiHolder implements InventoryHolder {

    private final Player owner;
    private Inventory inventory;

    public RebirthShopGuiHolder(Player owner) {
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
