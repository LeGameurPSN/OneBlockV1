package fr.tuto.oneblock.commands;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.gui.BankisItemsGUI;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.managers.VaultManager;
import fr.tuto.oneblock.models.IslandData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /seebankis <pseudo> (alias /seebanis) : commande ADMIN qui ouvre la caisse
 * d'île (objets + argent) d'un joueur pour la consulter/modifier, sans
 * passer par les permissions d'équipe habituelles (voir IslandData#canUseBank).
 *
 *  - Objets : ouvre directement le VRAI coffre partagé (le même Inventory
 *    que /bankis objets, voir BankisItemsGUI) : l'admin peut prendre,
 *    déposer, déplacer les objets librement, exactement comme un joueur
 *    normal dans son propre coffre - sauf qu'ici c'est celui d'un autre.
 *  - Argent : un message privé (visible uniquement par l'admin, jamais
 *    diffusé aux autres joueurs) avec des boutons cliquables pour
 *    ajouter/retirer des montants rapides, plus un bouton "montant
 *    personnalisé" qui fait passer l'admin en mode "saisie au chat" : son
 *    prochain message de chat est intercepté (jamais visible des autres
 *    joueurs, voir SeeBankisChatListener), interprété comme un montant, puis
 *    appliqué à la caisse - sans jamais passer par le chat public.
 */
public class SeeBankisCommand implements CommandExecutor, TabCompleter {

    private final OneBlockPlugin plugin;
    private final OneBlockManager manager;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final double[] QUICK_AMOUNTS = {100, 1000, 10000};

    /** Ce qu'un admin est en train de saisir au chat : quelle île, et ajout ou retrait. */
    public record PendingAmountInput(UUID islandOwner, String targetName, boolean add) {
    }

    // Un seul montant en attente de saisie par admin à la fois.
    private final Map<UUID, PendingAmountInput> pendingInputs = new HashMap<>();

    public SeeBankisCommand(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        if (!admin.hasPermission("oneblock.admin.bankis") && !admin.hasPermission("oneblock.admin")) {
            admin.sendMessage(LEGACY.deserialize("&cTu n'as pas la permission d'utiliser /seebankis."));
            return true;
        }

        if (args.length < 1) {
            admin.sendMessage(LEGACY.deserialize("&cUsage: &f/seebankis <pseudo>"));
            return true;
        }

        // Sous-commandes internes déclenchées par les boutons cliquables du
        // panneau argent (voir sendMoneyPanel) : jamais tapées à la main.
        if (args.length >= 2 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            handleQuickAmount(admin, args);
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("prompt")) {
            handlePrompt(admin, args);
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        IslandData island = manager.getIsland(target.getUniqueId());
        if (island == null) {
            admin.sendMessage(LEGACY.deserialize("&cAucune île OneBlock trouvée pour &f" + targetName + "&c."));
            return true;
        }

        BankisItemsGUI.open(manager, admin, island);
        sendMoneyPanel(admin, island, targetName);
        return true;
    }

    private void handleQuickAmount(Player admin, String[] args) {
        if (args.length != 3) return;
        String targetName = args[0];
        boolean add = args[1].equalsIgnoreCase("add");
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            return;
        }
        applyAmount(admin, targetName, add, amount);
    }

    private void handlePrompt(Player admin, String[] args) {
        if (args.length != 3) return;
        String targetName = args[0];
        boolean add = args[2].equalsIgnoreCase("add");

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        IslandData island = manager.getIsland(target.getUniqueId());
        if (island == null) {
            admin.sendMessage(LEGACY.deserialize("&cAucune île OneBlock trouvée pour &f" + targetName + "&c."));
            return;
        }

        pendingInputs.put(admin.getUniqueId(), new PendingAmountInput(island.getOwner(), targetName, add));
        admin.sendMessage(LEGACY.deserialize(
                (add ? "&a➕ Ajout" : "&c➖ Retrait") + " &7dans la caisse de &f" + targetName
                        + "&7 : tape le montant dans le chat &8(&7ou &f\"annuler\"&8)&7."));
        admin.sendMessage(LEGACY.deserialize("&8(&7Ce message ne sera vu par aucun autre joueur.&8)"));
    }

    /** Appelée par SeeBankisChatListener une fois le montant tapé au chat. */
    public void applyPendingAmount(Player admin, String rawAmount) {
        PendingAmountInput pending = pendingInputs.remove(admin.getUniqueId());
        if (pending == null) return;

        if (rawAmount.equalsIgnoreCase("annuler") || rawAmount.equalsIgnoreCase("cancel")) {
            admin.sendMessage(LEGACY.deserialize("&7Saisie annulée."));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(rawAmount.replace(",", "."));
        } catch (NumberFormatException e) {
            admin.sendMessage(LEGACY.deserialize("&cMontant invalide : &e" + rawAmount + "&c. Saisie annulée, relance le bouton pour réessayer."));
            return;
        }

        applyAmount(admin, pending.islandOwner(), pending.targetName(), pending.add(), amount);
    }

    /** true si cet admin a une saisie de montant en attente (utilisé par le listener de chat). */
    public boolean hasPending(UUID adminUuid) {
        return pendingInputs.containsKey(adminUuid);
    }

    private void applyAmount(Player admin, String targetName, boolean add, double amount) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        IslandData island = manager.getIsland(target.getUniqueId());
        if (island == null) {
            admin.sendMessage(LEGACY.deserialize("&cAucune île OneBlock trouvée pour &f" + targetName + "&c."));
            return;
        }
        applyAmount(admin, island.getOwner(), targetName, add, amount);
    }

    /**
     * Applique le montant à la caisse de l'île dont le PROPRIÉTAIRE est
     * islandOwner (résolu une seule fois, via UUID plutôt que re-cherché par
     * pseudo à chaque fois - notamment utile pour la saisie au chat, tapée
     * potentiellement plusieurs secondes après le clic sur le bouton).
     */
    private void applyAmount(Player admin, UUID islandOwner, String targetName, boolean add, double amount) {
        if (amount <= 0) {
            admin.sendMessage(LEGACY.deserialize("&cLe montant doit être positif."));
            return;
        }

        IslandData island = manager.getIsland(islandOwner);
        if (island == null) {
            admin.sendMessage(LEGACY.deserialize("&cCette île n'existe plus."));
            return;
        }

        VaultManager vault = plugin.getVaultManager();

        if (add) {
            island.addBankBalance(amount);
        } else {
            if (island.getBankBalance() < amount) {
                admin.sendMessage(LEGACY.deserialize(
                        "&cCaisse insuffisante : &e" + vault.format(island.getBankBalance()) + " &cdisponible."));
                return;
            }
            island.setBankBalance(island.getBankBalance() - amount);
        }
        manager.saveIslands();

        admin.sendMessage(LEGACY.deserialize(
                (add ? "&a✔ +" : "&c✔ -") + vault.format(amount) + " &7dans la caisse de &f" + targetName
                        + "&7. Nouveau solde : &e" + vault.format(island.getBankBalance()) + "&7."));

        sendMoneyPanel(admin, island, targetName);
    }

    /**
     * Message privé (jamais diffusé) avec le solde actuel et des boutons
     * cliquables pour ajouter/retirer des montants rapides ou saisir un
     * montant personnalisé au chat.
     */
    private void sendMoneyPanel(Player admin, IslandData island, String targetName) {
        VaultManager vault = plugin.getVaultManager();

        admin.sendMessage(LEGACY.deserialize("&8&m----&r &6Caisse de " + targetName + " &8&m----"));
        admin.sendMessage(LEGACY.deserialize("&7Solde actuel : &e" + vault.format(island.getBankBalance())));

        Component addLine = Component.text("Ajouter : ", NamedTextColor.GRAY);
        Component removeLine = Component.text("Retirer : ", NamedTextColor.GRAY);
        for (double amount : QUICK_AMOUNTS) {
            addLine = addLine.append(amountButton(targetName, true, amount)).append(Component.text(" "));
            removeLine = removeLine.append(amountButton(targetName, false, amount)).append(Component.text(" "));
        }
        addLine = addLine.append(customAmountButton(targetName, true));
        removeLine = removeLine.append(customAmountButton(targetName, false));

        admin.sendMessage(addLine);
        admin.sendMessage(removeLine);
        admin.sendMessage(LEGACY.deserialize("&8(&7Ce panneau n'est visible que par toi.&8)"));
    }

    private Component amountButton(String targetName, boolean add, double amount) {
        String amountStr = amount == Math.floor(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
        String command = "/seebankis " + targetName + " " + (add ? "add" : "remove") + " " + amountStr;
        Component label = Component.text((add ? "[+" : "[-") + formatShort(amount) + "]",
                add ? NamedTextColor.GREEN : NamedTextColor.RED);
        return label
                .hoverEvent(Component.text("Clique pour " + (add ? "ajouter " : "retirer ") + amountStr, NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private Component customAmountButton(String targetName, boolean add) {
        String command = "/seebankis " + targetName + " prompt " + (add ? "add" : "remove");
        Component label = Component.text("[Montant perso]", NamedTextColor.YELLOW, TextDecoration.UNDERLINED);
        return label
                .hoverEvent(Component.text("Clique puis tape un montant dans le chat (privé)", NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private String formatShort(double amount) {
        if (amount >= 1000) {
            return (amount % 1000 == 0 ? String.valueOf((long) (amount / 1000)) : String.valueOf(amount / 1000)) + "k";
        }
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(lower))
                    .toList();
        }
        return List.of();
    }
}
