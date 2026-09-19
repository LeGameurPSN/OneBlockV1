package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public SpawnCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande doit être exécutée par un joueur.");
            return true;
        }

        if (!player.hasPermission("oneblock.spawn")) {
            player.sendMessage(legacy.deserialize("&cTu n'as pas la permission d'utiliser /spawn."));
            return true;
        }

        Location spawnLoc = plugin.getManager().getServerSpawnLocation();
        if (spawnLoc == null) {
            String worldName = plugin.getConfig().getString("server-spawn.world", "world");
            player.sendMessage(legacy.deserialize("&cLe monde de spawn '" + worldName + "' est introuvable. Vérifie server-spawn.world dans config.yml."));
            return true;
        }

        // On retire la bordure personnelle du OneBlock : elle est liée au monde/
        // à l'île du joueur et n'a pas de sens une fois téléporté au spawn
        // (potentiellement dans un autre monde).
        player.setWorldBorder(null);
        plugin.getManager().stopBorderParticles(player);

        // Idem pour le vol : on ne garde le vol autorisé que sur l'île (sauf si
        // le joueur est en créatif/spectateur, qu'on ne touche pas).
        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            player.setAllowFlight(false);
            if (player.isFlying()) player.setFlying(false);
        }

        player.teleport(spawnLoc);
        player.sendMessage(legacy.deserialize("&a✔ Téléporté au spawn du serveur."));
        return true;
    }
}
