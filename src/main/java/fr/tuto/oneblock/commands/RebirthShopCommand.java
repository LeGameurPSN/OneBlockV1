package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.gui.RebirthShopGUI;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RebirthShopCommand implements CommandExecutor {

    private final OneBlockPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public RebirthShopCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("oneblock.rebirthshop")) {
            player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission d'utiliser /rebirthshop."));
            return true;
        }
        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage(LEGACY.deserialize("&cTu n'as pas d'île. Utilise &e/ob start &cpour en créer une."));
            return true;
        }
        RebirthShopGUI.open(plugin, player, island);
        return true;
    }
}
