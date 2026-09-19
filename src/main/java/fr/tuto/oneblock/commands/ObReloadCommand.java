package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /obreload : commande "libre-service" pour un joueur dont le bloc OneBlock
 * (overworld, Nether et/ou End) a disparu ou est resté bloqué en AIR suite à
 * un bug de régénération, SANS qu'un admin ait besoin d'intervenir
 * manuellement pour reposer un bloc.
 *
 * Elle remet simplement un bloc de TERRE (DIRT) à l'emplacement du/des
 * bloc(s) OneBlock de l'île du joueur (dans le/les monde(s) où ils se
 * trouvent), et réinitialise l'état de "matériau courant" de l'île sur DIRT
 * (plus aucun spawner mémorisé), afin que la mécanique de casse reparte sur
 * une base saine et cohérente avec ce qui est physiquement posé dans le
 * monde. Aucune récompense n'est donnée : c'est une réparation, pas une
 * casse.
 */
public class ObReloadCommand implements CommandExecutor {

    private final OneBlockManager manager;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public ObReloadCommand(OneBlockPlugin plugin) {
        this.manager = plugin.getManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        IslandData island = manager.getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage(legacy.deserialize("&cTu n'as pas encore d'île. Utilise &f/ob start&c."));
            return true;
        }

        // Sécurité anti-abus/anti-spam : seul le propriétaire ou un
        // coéquipier autorisé à casser peut déclencher la réparation de SA
        // propre île (pas celle d'un autre, et pas n'importe quel visiteur).
        if (!player.isOp() && !island.getOwner().equals(player.getUniqueId()) && !island.canBreak(player.getUniqueId())) {
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission de faire ça sur cette île."));
            return true;
        }

        int replaced = 0;
        replaced += replaceWithDirt(island.getBlockLocation());
        replaced += replaceWithDirt(island.getNetherBlockLocation());
        replaced += replaceWithDirt(island.getEndBlockLocation());

        // On remet l'état de l'île en cohérence avec ce qu'on vient de poser
        // physiquement (DIRT), pour que la prochaine casse fonctionne
        // normalement au lieu de redonner l'ancienne récompense/spawner
        // mémorisé sur un emplacement qui ne le contient plus réellement.
        island.setCurrentMaterial(Material.DIRT);
        island.setCurrentSpawnerEntity(null);
        island.setRegenerating(false);
        if (island.getNetherBlockLocation() != null) {
            island.setNetherCurrentMaterial(Material.DIRT);
            island.setNetherRegenerating(false);
        }
        if (island.getEndBlockLocation() != null) {
            island.setEndCurrentMaterial(Material.DIRT);
            island.setEndRegenerating(false);
        }

        if (replaced > 0) {
            player.sendMessage(legacy.deserialize("&a✔ Ton bloc OneBlock a été réparé (remplacé par de la terre). Tu peux le casser normalement."));
        } else {
            player.sendMessage(legacy.deserialize("&eAucun bloc à réparer n'a été trouvé (île pas encore générée dans ce monde)."));
        }
        return true;
    }

    private int replaceWithDirt(Location loc) {
        if (loc == null || loc.getWorld() == null) return 0;
        loc.getBlock().setType(Material.DIRT);
        return 1;
    }
}
