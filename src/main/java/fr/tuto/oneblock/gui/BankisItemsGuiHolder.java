package fr.tuto.oneblock.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marqueur utilisé pour reconnaître le coffre partagé de la caisse d'île
 * (/bankis objets) dans le listener.
 *
 * Contrairement aux autres GUI du plugin (boutiques, classements...), celui-ci
 * n'est PAS recréé à chaque ouverture : OneBlockManager en garde UNE seule
 * instance vivante par île (voir getOrCreateBankInventory), afin que tous les
 * coéquipiers qui l'ouvrent en même temps voient exactement le même contenu
 * se mettre à jour en direct - comme un coffre partagé classique. Il n'est
 * donc pas verrouillé/annulé par GuiListener : les clics s'y comportent
 * comme dans n'importe quel coffre (dépôt/retrait libres une fois l'accès
 * autorisé à l'ouverture, voir BankisCommand et IslandData#canUseBank).
 */
public class BankisItemsGuiHolder implements InventoryHolder {

    private final UUID islandOwner;
    private Inventory inventory;

    public BankisItemsGuiHolder(UUID islandOwner) {
        this.islandOwner = islandOwner;
    }

    /** UUID du propriétaire de l'île (clé de l'île dans OneBlockManager), pas forcément le joueur qui a ouvert le GUI. */
    public UUID getIslandOwner() {
        return islandOwner;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
