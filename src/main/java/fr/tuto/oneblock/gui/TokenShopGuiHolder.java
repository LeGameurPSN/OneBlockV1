package fr.tuto.oneblock.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur utilisé pour reconnaître de façon fiable l'inventaire de la
 * boutique à tokens (/token) dans le listener, séparément du GUI de
 * paramètres (OneBlockGuiHolder).
 */
public class TokenShopGuiHolder implements InventoryHolder {

    private final Player owner;
    private Inventory inventory;

    public TokenShopGuiHolder(Player owner) {
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
