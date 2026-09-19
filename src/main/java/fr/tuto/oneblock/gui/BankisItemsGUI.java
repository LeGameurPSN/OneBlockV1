package fr.tuto.oneblock.gui;

import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Coffre partagé de la caisse d'île (/bankis objets) : un simple double
 * coffre (54 cases) où propriétaire, coéquipiers et joueurs de confiance
 * ayant la permission "bank" (voir IslandData#canUseBank) peuvent librement
 * déposer et retirer des objets, exactement comme dans la caisse d'argent.
 *
 * L'Inventory est mis en cache par île dans OneBlockManager et réutilisé à
 * chaque ouverture (jamais recréé tant que le plugin tourne), pour que tous
 * les coéquipiers qui l'ouvrent en même temps partagent le même contenu en
 * direct.
 */
public class BankisItemsGUI {

    public static final int SIZE = 54;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private BankisItemsGUI() {
    }

    /** Ouvre (en le créant si besoin) le coffre partagé de la caisse de cette île pour ce joueur. */
    public static void open(OneBlockManager manager, Player viewer, IslandData island) {
        Inventory inv = manager.getOrCreateBankInventory(island);
        viewer.openInventory(inv);
    }

    /** Construit l'Inventory (à appeler une seule fois par île, depuis OneBlockManager). */
    public static Inventory create(IslandData island) {
        BankisItemsGuiHolder holder = new BankisItemsGuiHolder(island.getOwner());
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8Caisse d'île &7- &fObjets"));
        holder.setInventory(inv);

        var stored = island.getBankItems();
        if (stored != null) {
            inv.setContents(stored.length == SIZE ? stored : resize(stored));
        }
        return inv;
    }

    private static org.bukkit.inventory.ItemStack[] resize(org.bukkit.inventory.ItemStack[] source) {
        org.bukkit.inventory.ItemStack[] resized = new org.bukkit.inventory.ItemStack[SIZE];
        System.arraycopy(source, 0, resized, 0, Math.min(source.length, SIZE));
        return resized;
    }
}
