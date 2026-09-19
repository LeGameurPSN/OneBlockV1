package fr.tuto.oneblock.gui;

import fr.tuto.oneblock.models.Role;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marqueur utilisé pour reconnaître le GUI /ob perms (RolePermissionsGUI)
 * dans le listener. Mémorise l'onglet de rôle actif (VISITEUR/TRUST/MEMBRE).
 */
public class RolePermissionsGuiHolder implements InventoryHolder {

    private final Player owner;
    private final UUID islandOwner;
    private Role activeRole = Role.VISITEUR;
    private Inventory inventory;

    public RolePermissionsGuiHolder(Player owner, UUID islandOwner) {
        this.owner = owner;
        this.islandOwner = islandOwner;
    }

    public Player getOwner() {
        return owner;
    }

    public UUID getIslandOwner() {
        return islandOwner;
    }

    public Role getActiveRole() {
        return activeRole;
    }

    public void setActiveRole(Role activeRole) {
        this.activeRole = activeRole;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
