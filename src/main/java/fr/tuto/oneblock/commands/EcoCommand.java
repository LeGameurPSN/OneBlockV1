package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.managers.VaultManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /eco give|take|set <joueur> <montant> — commande d'administration pour
 * gérer l'argent Vault d'un joueur (en ligne ou hors-ligne).
 * <p>
 * Le montant doit être compris entre 1 et 5000 (bornes configurables via
 * eco.min-amount / eco.max-amount dans config.yml) pour éviter les erreurs de
 * frappe qui donneraient/retireraient des sommes astronomiques d'un coup.
 * Réservée à LeGameurPSN_YT (les ops ne suffisent plus). La console reste
 * autorisée : c'est elle qui verse les gains automatiques du système de
 * trivia (voir TriviaManager).
 */
public class EcoCommand implements CommandExecutor, TabCompleter {

    private static final String ALLOWED_PLAYER = "LeGameurPSN_YT";

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public EcoCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !player.getName().equalsIgnoreCase(ALLOWED_PLAYER)) {
            msg(sender, "&cCette commande est réservée à " + ALLOWED_PLAYER + ".");
            return true;
        }

        VaultManager vault = plugin.getVaultManager();
        if (!vault.isEnabled()) {
            msg(sender, "&cAucune économie détectée (Vault + plugin d'économie requis).");
            return true;
        }

        if (args.length < 3) {
            msg(sender, "&cUsage : /eco <give|take|set> <joueur> <montant>");
            return true;
        }

        String action = args[0].toLowerCase();
        if (!action.equals("give") && !action.equals("take") && !action.equals("set")) {
            msg(sender, "&cUsage : /eco <give|take|set> <joueur> <montant>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            msg(sender, "&cJoueur \"" + args[1] + "\" introuvable (jamais connecté sur ce serveur).");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            msg(sender, "&cMontant invalide : \"" + args[2] + "\".");
            return true;
        }

        double min = plugin.getConfig().getDouble("eco.min-amount", 1);
        double max = plugin.getConfig().getDouble("eco.max-amount", 5000);
        if (amount < min || amount > max) {
            msg(sender, "&cLe montant doit être compris entre &f" + vault.format(min)
                    + " &cet &f" + vault.format(max) + "&c.");
            return true;
        }

        boolean success;
        switch (action) {
            case "give" -> success = vault.deposit(target, amount);
            case "take" -> {
                double balance = vault.getBalance(target);
                if (balance < amount) {
                    msg(sender, "&c" + target.getName() + " n'a que &f" + vault.format(balance)
                            + " &c, impossible de lui retirer &f" + vault.format(amount) + "&c.");
                    return true;
                }
                success = vault.withdraw(target, amount);
            }
            default -> { // set
                double current = vault.getBalance(target);
                double diff = amount - current;
                success = diff >= 0 ? vault.deposit(target, diff) : vault.withdraw(target, -diff);
            }
        }

        if (!success) {
            msg(sender, "&cLa transaction a échoué, réessaie.");
            return true;
        }

        String amountText = vault.format(amount);
        String verbPhrase = switch (action) {
            case "give" -> "&adonné &f" + amountText + " &aà &f" + target.getName();
            case "take" -> "&aretiré &f" + amountText + " &aà &f" + target.getName();
            default -> "&adéfini le solde de &f" + target.getName() + " &aà &f" + amountText;
        };
        msg(sender, "&a✔ " + verbPhrase + "&a.");

        if (target.isOnline()) {
            var onlineTarget = target.getPlayer();
            if (onlineTarget != null) {
                switch (action) {
                    case "give" -> msg(onlineTarget, "&aUn administrateur t'a donné &f" + vault.format(amount) + "&a !");
                    case "take" -> msg(onlineTarget, "&cUn administrateur t'a retiré &f" + vault.format(amount) + "&c.");
                    default -> msg(onlineTarget, "&eUn administrateur a défini ton solde à &f" + vault.format(amount) + "&e.");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("give", "take", "set");
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            return Arrays.asList("1", "100", "1000", "5000");
        }
        return new ArrayList<>();
    }

    private void msg(CommandSender target, String text) {
        Component c = legacy.deserialize(text);
        target.sendMessage(c);
    }
}
