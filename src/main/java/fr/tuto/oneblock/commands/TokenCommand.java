package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.gui.TokenShopGUI;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class TokenCommand implements CommandExecutor, TabCompleter {

    private final OneBlockPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public TokenCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // --- Sous-commandes admin : /token give|take <pseudo> <montant> ---
        // Ciblent toujours le solde PERSONNEL du joueur visé, pas toute son équipe.
        if (args.length >= 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            if (!sender.hasPermission("oneblock.admin.token") && !sender.hasPermission("oneblock.admin")) {
                sender.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission &7oneblock.admin.token&c."));
                return true;
            }
            String targetName = args[1];
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(LEGACY.deserialize("&cMontant invalide : &e" + args[2]));
                return true;
            }
            if (amount <= 0) {
                sender.sendMessage(LEGACY.deserialize("&cLe montant doit être positif."));
                return true;
            }

            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (!target.hasPlayedBefore()) {
                sender.sendMessage(LEGACY.deserialize("&cJoueur introuvable : &e" + targetName));
                return true;
            }
            IslandData island = plugin.getManager().getIsland(target.getUniqueId());
            if (island == null) {
                sender.sendMessage(LEGACY.deserialize("&e" + targetName + " &cn'a pas d'île."));
                return true;
            }
            java.util.UUID targetUuid = target.getUniqueId();
            if (args[0].equalsIgnoreCase("give")) {
                island.addTokens(targetUuid, amount);
                sender.sendMessage(LEGACY.deserialize("&a✔ &e" + amount + " &atokens donnés à &e" + targetName
                        + " &a(solde perso : &e" + island.getTokens(targetUuid) + "&a)."));
            } else {
                int newVal = Math.max(0, island.getTokens(targetUuid) - amount);
                island.setTokens(targetUuid, newVal);
                sender.sendMessage(LEGACY.deserialize("&a✔ &e" + amount + " &atokens retirés à &e" + targetName
                        + " &a(solde perso : &e" + newVal + "&a)."));
            }
            plugin.getManager().saveIslands();
            return true;
        }

        // --- Commandes joueur ---
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        if (!player.hasPermission("oneblock.token")) {
            player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission d'utiliser /token."));
            return true;
        }

        IslandData island = plugin.getManager().getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage(LEGACY.deserialize("&cTu n'as pas d'île. Utilise &e/ob start &cpour en créer une."));
            return true;
        }

        // /token [shop|buy fly]
        if (args.length == 0 || args[0].equalsIgnoreCase("shop")) {
            if (!player.hasPermission("oneblock.token.shop")) {
                player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission d'ouvrir la boutique à tokens (&7oneblock.token.shop&c)."));
                return true;
            }
            TokenShopGUI.open(plugin, player, island);
            return true;
        }

        if (args[0].equalsIgnoreCase("buy") && args.length >= 2) {
            String item = args[1].toLowerCase();
            java.util.UUID uuid = player.getUniqueId();

            // L'achat du vol a sa propre permission dédiée.
            if (item.equals("fly") && !player.hasPermission("oneblock.token.fly")) {
                player.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission d'acheter le vol (&7oneblock.token.fly&c)."));
                return true;
            }

            int cost = plugin.getTokenConfig().getInt("token." + item + "-cost", -1);
            if (cost < 0) {
                player.sendMessage(LEGACY.deserialize("&cArticle inconnu : &e" + item));
                return true;
            }
            int balance = island.getTokens(uuid);
            if (balance < cost) {
                player.sendMessage(LEGACY.deserialize("&cTokens insuffisants : tu as &e" + balance
                        + " &ctokens, il t'en faut &e" + cost + "&c."));
                return true;
            }
            island.setTokens(uuid, balance - cost);
            // Appliquer l'effet selon l'article (extensible)
            if (item.equals("fly")) {
                island.setFlyUnlocked(true);
                player.sendMessage(LEGACY.deserialize("&a✔ Vol débloqué sur ton île !"));
            } else {
                player.sendMessage(LEGACY.deserialize("&a✔ Article &e" + item + " &aacheté !"));
            }
            plugin.getManager().saveIslands();
            return true;
        }

        // Solde : ton propre nombre de tokens, et si tu es en équipe, le détail
        // (nom + tokens) de chaque coéquipier ainsi que le total de l'équipe.
        sendBalance(player, island);
        return true;
    }

    private void sendBalance(Player player, IslandData island) {
        java.util.UUID uuid = player.getUniqueId();
        player.sendMessage(LEGACY.deserialize("&7Ton solde : &e" + island.getTokens(uuid)
                + " tokens &7| &e/token shop &7pour ouvrir la boutique."));

        Map<java.util.UUID, Integer> breakdown = island.getTeamTokensBreakdown();
        if (breakdown.size() <= 1) {
            return; // pas d'équipe : rien de plus à afficher
        }

        player.sendMessage(LEGACY.deserialize("&8&m----&r &b&lTokens de l'équipe &8&m----"));
        for (Map.Entry<java.util.UUID, Integer> entry : breakdown.entrySet()) {
            @SuppressWarnings("deprecation")
            OfflinePlayer member = Bukkit.getOfflinePlayer(entry.getKey());
            String name = member.getName() != null ? member.getName() : "?";
            boolean self = entry.getKey().equals(uuid);
            player.sendMessage(LEGACY.deserialize((self ? "&e▶ &f" : "&7- &f") + name
                    + " &7: &e" + entry.getValue() + " tokens"));
        }
        player.sendMessage(LEGACY.deserialize("&7Total équipe : &e" + island.getTeamTokensTotal() + " tokens"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("shop", "buy", "give", "take").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            return List.of("fly", "hat", "feed").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
