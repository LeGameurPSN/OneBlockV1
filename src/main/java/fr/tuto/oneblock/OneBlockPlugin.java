package fr.tuto.oneblock;

import fr.tuto.oneblock.commands.BankisCommand;
import fr.tuto.oneblock.commands.AfkCommand;
import fr.tuto.oneblock.commands.BoostCommand;
import fr.tuto.oneblock.commands.CineTestCommand;
import fr.tuto.oneblock.commands.ChelpCommand;
import fr.tuto.oneblock.commands.MsgCommand;
import fr.tuto.oneblock.commands.QTopCommand;
import fr.tuto.oneblock.commands.SendQuestionCommand;
import fr.tuto.oneblock.commands.EcoCommand;
import fr.tuto.oneblock.commands.IsTransferCommand;
import fr.tuto.oneblock.commands.OBCommand;
import fr.tuto.oneblock.commands.ObReloadCommand;
import fr.tuto.oneblock.commands.RebirthShopCommand;
import fr.tuto.oneblock.commands.SeeBankisCommand;
import fr.tuto.oneblock.commands.SpawnCommand;
import fr.tuto.oneblock.commands.StaffChatCommand;
import fr.tuto.oneblock.commands.TokenCommand;
import fr.tuto.oneblock.commands.TopCommand;
import fr.tuto.oneblock.commands.UpgradesCommand;
import fr.tuto.oneblock.listeners.BlockBreakListener;
import fr.tuto.oneblock.listeners.GuiListener;
import fr.tuto.oneblock.listeners.IslandSettingsListener;
import fr.tuto.oneblock.listeners.MobKillListener;
import fr.tuto.oneblock.listeners.NetherBlockListener;
import fr.tuto.oneblock.listeners.PlayerJoinListener;
import fr.tuto.oneblock.listeners.PlayerQuitListener;
import fr.tuto.oneblock.listeners.PlayerRespawnListener;
import fr.tuto.oneblock.listeners.SeeBankisChatListener;
import fr.tuto.oneblock.listeners.TeamChatListener;
import fr.tuto.oneblock.listeners.TriviaChatListener;
import fr.tuto.oneblock.managers.OneBlockManager;
import fr.tuto.oneblock.managers.AfkZoneManager;
import fr.tuto.oneblock.managers.BoostManager;
import fr.tuto.oneblock.managers.BossBarManager;
import fr.tuto.oneblock.managers.CinematicManager;
import fr.tuto.oneblock.managers.TriviaManager;
import fr.tuto.oneblock.managers.VaultManager;
import fr.tuto.oneblock.placeholders.OneBlockExpansion;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class OneBlockPlugin extends JavaPlugin {

    private static OneBlockPlugin instance;
    private OneBlockManager manager;
    private VaultManager vaultManager;
    private fr.tuto.oneblock.managers.LuckPermsManager luckPermsManager;
    private BossBarManager bossBarManager;
    private AfkZoneManager afkZoneManager;
    private CinematicManager cinematicManager;
    private TriviaManager triviaManager;
    private BoostManager boostManager;

    // Fichier séparé pour la config de la renaissance (/ob renaissance + /rebirthshop)
    private File rebirthConfigFile;
    private FileConfiguration rebirthConfig;

    // Fichier séparé pour la config de la boutique à tokens (/token)
    private File tokenConfigFile;
    private FileConfiguration tokenConfig;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadRebirthConfig();
        loadTokenConfig();

        manager = new OneBlockManager(this);
        manager.loadConfig();
        manager.initWorld();
        manager.loadIslands();

        vaultManager = new VaultManager(this);
        luckPermsManager = new fr.tuto.oneblock.managers.LuckPermsManager(this);
        bossBarManager = new BossBarManager(this);
        cinematicManager = new CinematicManager(this);

        afkZoneManager = new AfkZoneManager(this);
        afkZoneManager.loadConfig();
        afkZoneManager.start();

        triviaManager = new TriviaManager(this);
        triviaManager.start();

        boostManager = new BoostManager(this);
        boostManager.start();

        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new IslandSettingsListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new MobKillListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(this), this);
        getServer().getPluginManager().registerEvents(new NetherBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new TriviaChatListener(this), this);
        getServer().getPluginManager().registerEvents(new fr.tuto.oneblock.listeners.BoostItemListener(this), this);

        var seeBankisExecutor = new SeeBankisCommand(this);
        getServer().getPluginManager().registerEvents(new SeeBankisChatListener(this, seeBankisExecutor), this);
        getServer().getPluginManager().registerEvents(new fr.tuto.oneblock.listeners.SpawnerStackListener(this), this);

        var obExecutor = new OBCommand(this);
        var obCommand = getCommand("ob");
        if (obCommand != null) {
            obCommand.setExecutor(obExecutor);
            obCommand.setTabCompleter(obExecutor);
        }
        var isCommand = getCommand("is");
        if (isCommand != null) {
            isCommand.setExecutor(obExecutor);
            isCommand.setTabCompleter(obExecutor);
        }
        var rebirthCommand = getCommand("rebirth");
        if (rebirthCommand != null) {
            rebirthCommand.setExecutor(obExecutor);
            rebirthCommand.setTabCompleter(obExecutor);
        }
        var spawnCommand = getCommand("spawn");
        if (spawnCommand != null) {
            spawnCommand.setExecutor(new SpawnCommand(this));
        }
        var topExecutor = new TopCommand(this);
        var topCommand = getCommand("top");
        if (topCommand != null) {
            topCommand.setExecutor(topExecutor);
            topCommand.setTabCompleter(topExecutor);
        }
        var bankisExecutor = new BankisCommand(this);
        var bankisCommand = getCommand("bankis");
        if (bankisCommand != null) {
            bankisCommand.setExecutor(bankisExecutor);
            bankisCommand.setTabCompleter(bankisExecutor);
        }
        var tokenExecutor = new TokenCommand(this);
        var tokenCommand = getCommand("token");
        if (tokenCommand != null) {
            tokenCommand.setExecutor(tokenExecutor);
            tokenCommand.setTabCompleter(tokenExecutor);
        }
        var upgradesCommand = getCommand("upgrades");
        if (upgradesCommand != null) {
            upgradesCommand.setExecutor(new UpgradesCommand(this));
        }
        var rebirthShopCommand = getCommand("rebirthshop");
        if (rebirthShopCommand != null) {
            rebirthShopCommand.setExecutor(new RebirthShopCommand(this));
        }
        var isTransferCommand = getCommand("istransfer");
        if (isTransferCommand != null) {
            var isTransferExecutor = new IsTransferCommand(this);
            isTransferCommand.setExecutor(isTransferExecutor);
            isTransferCommand.setTabCompleter(isTransferExecutor);
        }
        var afkCommand = getCommand("afk");
        if (afkCommand != null) {
            afkCommand.setExecutor(new AfkCommand(this));
        }
        var seeBankisCommand = getCommand("seebankis");
        if (seeBankisCommand != null) {
            seeBankisCommand.setExecutor(seeBankisExecutor);
            seeBankisCommand.setTabCompleter(seeBankisExecutor);
        }

        var cineTestCommand = getCommand("cinetest");
        if (cineTestCommand != null) {
            cineTestCommand.setExecutor(new CineTestCommand(this));
        }
        var chelpCommand = getCommand("chelp");
        if (chelpCommand != null) {
            chelpCommand.setExecutor(new ChelpCommand(this));
        }
        var msgExecutor = new MsgCommand(this);
        var msgCommand = getCommand("msg");
        if (msgCommand != null) {
            msgCommand.setExecutor(msgExecutor);
            msgCommand.setTabCompleter(msgExecutor);
        }
        var sendQuestionCommand = getCommand("sendquestion");
        if (sendQuestionCommand != null) {
            sendQuestionCommand.setExecutor(new SendQuestionCommand(this));
        }
        var qTopExecutor = new QTopCommand(this);
        var qTopCommand = getCommand("qtop");
        if (qTopCommand != null) {
            qTopCommand.setExecutor(qTopExecutor);
            qTopCommand.setTabCompleter(qTopExecutor);
        }
        var ecoExecutor = new EcoCommand(this);
        var ecoCommand = getCommand("eco");
        if (ecoCommand != null) {
            ecoCommand.setExecutor(ecoExecutor);
            ecoCommand.setTabCompleter(ecoExecutor);
        }

        var obReloadCommand = getCommand("obreload");
        if (obReloadCommand != null) {
            obReloadCommand.setExecutor(new ObReloadCommand(this));
        }

        var staffChatCommand = getCommand("staffchat");
        if (staffChatCommand != null) {
            staffChatCommand.setExecutor(new StaffChatCommand(this));
        }

        var boostExecutor = new BoostCommand(this);
        var boostCommand = getCommand("boost");
        if (boostCommand != null) {
            boostCommand.setExecutor(boostExecutor);
            boostCommand.setTabCompleter(boostExecutor);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new OneBlockExpansion(this).register();
            getLogger().info("PlaceholderAPI détecté : placeholders %oneblock_...% enregistrés.");
        } else {
            getLogger().info("PlaceholderAPI non trouvé : les placeholders %oneblock_...% ne seront pas disponibles.");
        }

        getLogger().info("OneBlock activé !");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveIslands();
        }
        if (afkZoneManager != null) {
            afkZoneManager.stop();
        }
        if (triviaManager != null) {
            triviaManager.stop();
            triviaManager.saveStats();
        }
        if (boostManager != null) {
            boostManager.stop();
        }
        getLogger().info("OneBlock désactivé, îles sauvegardées.");
    }

    // ==================== rebirth.yml ====================

    /**
     * Copie rebirth.yml depuis le jar si absent, puis le charge. Les
     * valeurs par défaut embarquées dans le jar servent de secours pour
     * toute clé manquante (comme saveDefaultConfig()/getConfig() pour
     * config.yml).
     */
    public void loadRebirthConfig() {
        if (rebirthConfigFile == null) {
            rebirthConfigFile = new File(getDataFolder(), "rebirth.yml");
        }
        if (!rebirthConfigFile.exists()) {
            saveResource("rebirth.yml", false);
        }
        rebirthConfig = YamlConfiguration.loadConfiguration(rebirthConfigFile);
        applyDefaults(rebirthConfig, "rebirth.yml");
    }

    public void reloadRebirthConfig() {
        if (rebirthConfigFile == null) {
            rebirthConfigFile = new File(getDataFolder(), "rebirth.yml");
        }
        rebirthConfig = YamlConfiguration.loadConfiguration(rebirthConfigFile);
        applyDefaults(rebirthConfig, "rebirth.yml");
    }

    public FileConfiguration getRebirthConfig() {
        if (rebirthConfig == null) loadRebirthConfig();
        return rebirthConfig;
    }

    // ==================== token.yml ====================

    /** Copie token.yml depuis le jar si absent, puis le charge (voir loadRebirthConfig()). */
    public void loadTokenConfig() {
        if (tokenConfigFile == null) {
            tokenConfigFile = new File(getDataFolder(), "token.yml");
        }
        if (!tokenConfigFile.exists()) {
            saveResource("token.yml", false);
        }
        tokenConfig = YamlConfiguration.loadConfiguration(tokenConfigFile);
        applyDefaults(tokenConfig, "token.yml");
    }

    public void reloadTokenConfig() {
        if (tokenConfigFile == null) {
            tokenConfigFile = new File(getDataFolder(), "token.yml");
        }
        tokenConfig = YamlConfiguration.loadConfiguration(tokenConfigFile);
        applyDefaults(tokenConfig, "token.yml");
    }

    public FileConfiguration getTokenConfig() {
        if (tokenConfig == null) loadTokenConfig();
        return tokenConfig;
    }

    /** Applique les valeurs par défaut embarquées dans le jar à une config chargée depuis le disque. */
    private void applyDefaults(FileConfiguration config, String resourceName) {
        InputStream defStream = getResource(resourceName);
        if (defStream == null) return;
        try (InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            config.setDefaults(defaults);
        } catch (java.io.IOException e) {
            getLogger().warning("Impossible de charger les valeurs par défaut de " + resourceName + " : " + e.getMessage());
        }
    }

    public static OneBlockPlugin getInstance() {
        return instance;
    }

    public OneBlockManager getManager() {
        return manager;
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    public fr.tuto.oneblock.managers.LuckPermsManager getLuckPermsManager() {
        return luckPermsManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public BoostManager getBoostManager() {
        return boostManager;
    }

    public AfkZoneManager getAfkZoneManager() {
        return afkZoneManager;
    }

    public CinematicManager getCinematicManager() {
        return cinematicManager;
    }

    public TriviaManager getTriviaManager() {
        return triviaManager;
    }

}
