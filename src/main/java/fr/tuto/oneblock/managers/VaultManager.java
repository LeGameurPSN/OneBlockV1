package fr.tuto.oneblock.managers;

import fr.tuto.oneblock.OneBlockPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Petite couche au-dessus de Vault pour gérer l'argent in-game
 * (compatible EssentialsX Economy, ou tout autre plugin d'économie
 * qui s'accroche à Vault).
 */
public class VaultManager {

    private final OneBlockPlugin plugin;
    private Economy economy;

    public VaultManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault non trouvé : les fonctionnalités liées à l'argent (agrandissement d'île) sont désactivées.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("Aucun plugin d'économie détecté via Vault (installe EssentialsX par exemple).");
            return false;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info("Économie détectée via Vault : " + economy.getName());
        return true;
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public double getBalance(Player player) {
        if (!isEnabled()) return 0;
        return economy.getBalance(player);
    }

    /**
     * Solde d'un joueur potentiellement hors-ligne (utilisé par /top pour
     * classer les joueurs par argent sans qu'ils soient forcément connectés).
     */
    public double getBalance(OfflinePlayer player) {
        if (!isEnabled()) return 0;
        return economy.getBalance(player);
    }

    public boolean has(Player player, double amount) {
        if (!isEnabled()) return false;
        return economy.has(player, amount);
    }

    /**
     * Retire l'argent au joueur. Retourne true si la transaction a réussi.
     */
    public boolean withdraw(Player player, double amount) {
        if (!isEnabled()) return false;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Retire l'argent à un joueur potentiellement hors-ligne (utilisé par
     * /eco take et /eco set).
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isEnabled()) return false;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Dépose de l'argent sur le compte du joueur (utilisé par /bankis retirer).
     * Retourne true si la transaction a réussi.
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isEnabled()) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        if (isEnabled()) {
            return economy.format(amount);
        }
        return String.valueOf(amount);
    }
}
