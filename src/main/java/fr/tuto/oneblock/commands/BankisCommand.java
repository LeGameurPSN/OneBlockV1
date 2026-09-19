package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.gui.BankisItemsGUI;
import fr.tuto.oneblock.managers.VaultManager;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class BankisCommand implements CommandExecutor, TabCompleter {

    private final OneBlockPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public BankisCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        if (!player.hasPermission("oneblock.bankis")) {
            player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission d'utiliser /bankis."));
            return true;
        }

        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage(LEGACY.deserialize("&cTu n'as pas d'île. Utilise &e/ob start &cpour en créer une."));
            return true;
        }

        VaultManager vault = plugin.getVaultManager();

        if (args.length >= 1 && (args[0].equalsIgnoreCase("objets") || args[0].equalsIgnoreCase("items"))) {
            openItems(player, island);
            return true;
        }

        if (!vault.isEnabled()) {
            player.sendMessage(LEGACY.deserialize("&cVault n'est pas disponible sur ce serveur."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player, island, vault);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (args.length < 2) {
            sendHelp(player, island, vault);
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(LEGACY.deserialize("&cMontant invalide : &e" + args[1]));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(LEGACY.deserialize("&cLe montant doit être positif."));
            return true;
        }

        OfflinePlayer offlinePlayer = player;

        switch (sub) {
            case "deposer" -> {
                if (!player.hasPermission("oneblock.bankis.deposer")) {
                    player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission de déposer dans la caisse d'île."));
                    return true;
                }
                // Même permission d'île que le retrait ("bank", voir /ob permissions) :
                // le propriétaire a toujours accès, un coéquipier/joueur de confiance
                // en dispose par défaut dès son arrivée dans l'équipe.
                if (!island.canUseBank(player.getUniqueId())) {
                    player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission d'utiliser la caisse de cette île."));
                    return true;
                }
                double balance = vault.getBalance(offlinePlayer);
                if (balance < amount) {
                    player.sendMessage(LEGACY.deserialize(
                            "&cSolde insuffisant : tu as &e" + vault.format(balance) + "&c, il te faut &e" + vault.format(amount) + "&c."));
                    return true;
                }
                vault.withdraw(player, amount);
                island.setBankBalance(island.getBankBalance() + amount);
                plugin.getManager().saveIslands();
                player.sendMessage(LEGACY.deserialize(
                        "&a✔ &e" + vault.format(amount) + " &adéposé dans la caisse d'île. Solde caisse : &e" + vault.format(island.getBankBalance()) + "&a."));
            }
            case "retirer" -> {
                if (!player.hasPermission("oneblock.bankis.retirer")) {
                    player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission de retirer de la caisse d'île."));
                    return true;
                }
                if (island.getBankBalance() < amount) {
                    player.sendMessage(LEGACY.deserialize(
                            "&cCaisse insuffisante : &e" + vault.format(island.getBankBalance()) + " &cdisponible."));
                    return true;
                }
                // Le propriétaire peut toujours retirer ; un coéquipier ou un
                // joueur de confiance doit avoir la permission "bank" (voir
                // /ob permissions, colonne "Banque"), accordée par défaut.
                if (!island.canUseBank(player.getUniqueId())) {
                    player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission de retirer de la caisse de cette île."));
                    return true;
                }
                island.setBankBalance(island.getBankBalance() - amount);
                vault.deposit(offlinePlayer, amount);
                plugin.getManager().saveIslands();
                player.sendMessage(LEGACY.deserialize(
                        "&a✔ &e" + vault.format(amount) + " &aretirés de la caisse d'île. Solde caisse : &e" + vault.format(island.getBankBalance()) + "&a."));
            }
            default -> sendHelp(player, island, vault);
        }
        return true;
    }

    /**
     * Ouvre le coffre partagé de la caisse d'île (dépôt/retrait d'objets),
     * réservé au propriétaire et aux coéquipiers/joueurs de confiance ayant
     * la permission "bank" (même permission que l'argent, accordée par
     * défaut à l'arrivée dans l'équipe : voir IslandData#canUseBank).
     */
    private void openItems(Player player, IslandData island) {
        if (!player.hasPermission("oneblock.bankis.objets")) {
            player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission d'utiliser /bankis objets."));
            return;
        }
        if (!island.canUseBank(player.getUniqueId())) {
            player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission d'utiliser la caisse de cette île."));
            return;
        }
        BankisItemsGUI.open(plugin.getManager(), player, island);
    }

    private void sendHelp(Player player, IslandData island, VaultManager vault) {
        player.sendMessage(LEGACY.deserialize("&8&m----&r &b/bankis &8&m----"));
        player.sendMessage(LEGACY.deserialize("&7Caisse d'île : &e" + vault.format(island.getBankBalance())));
        player.sendMessage(LEGACY.deserialize("&e/bankis deposer <montant> &7- Déposer de ton argent dans la caisse"));
        player.sendMessage(LEGACY.deserialize("&e/bankis retirer <montant> &7- Retirer de la caisse"));
        player.sendMessage(LEGACY.deserialize("&e/bankis objets &7- Ouvrir le coffre d'objets partagé de la caisse"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("deposer", "retirer", "objets").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
