package fr.tuto.oneblock.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Marqueur du GUI {@code /ob menu} (voir OBMenuGUI) : retient la catégorie
 * actuellement affichée (VISITE ou SANCTION) et la correspondance
 * slot -> UUID des têtes de joueurs affichées, pour que le listener sache
 * quel joueur a été ciblé par un clic sans avoir à relire l'inventaire.
 */
public class OBMenuGuiHolder implements InventoryHolder {

    /** Catégorie active du menu : détermine l'action des clics gauche/droit. */
    public enum Category {
        VISITE,
        SANCTION
    }

    private final Player owner;
    private Inventory inventory;
    private Category category;
    private Map<Integer, UUID> slotTargets;

    public OBMenuGuiHolder(Player owner, Category category) {
        this.owner = owner;
        this.category = category;
    }

    public Player getOwner() {
        return owner;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Map<Integer, UUID> getSlotTargets() {
        return slotTargets;
    }

    public void setSlotTargets(Map<Integer, UUID> slotTargets) {
        this.slotTargets = slotTargets;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
