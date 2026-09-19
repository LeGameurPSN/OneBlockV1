package fr.tuto.oneblock.managers;

import fr.tuto.oneblock.OneBlockPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Petite couche au-dessus de LuckPerms pour vérifier une permission sur un
 * joueur potentiellement hors-ligne (ex: oneblock.ban.bypass), ce que l'API
 * Bukkit standard ne permet pas de faire.
 */
public class LuckPermsManager {

    private final OneBlockPlugin plugin;
    private LuckPerms luckPerms;

    public LuckPermsManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("LuckPerms non trouvé : les permissions hors-ligne (ex: oneblock.ban.bypass) ne pourront pas être vérifiées pour un joueur déconnecté.");
            return false;
        }
        try {
            luckPerms = LuckPermsProvider.get();
            plugin.getLogger().info("LuckPerms détecté : vérification des permissions hors-ligne activée.");
            return true;
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("LuckPerms trouvé mais son API n'est pas encore prête.");
            return false;
        }
    }

    public boolean isEnabled() {
        return luckPerms != null;
    }

    /**
     * Vérifie une permission pour un joueur, même hors-ligne. Si LuckPerms
     * n'est pas disponible, ou si le chargement des données prend trop de
     * temps ou échoue, retourne false (pas de faux positif de protection).
     */
    public boolean hasPermission(OfflinePlayer target, String permission) {
        if (!isEnabled()) return false;
        UUID uuid = target.getUniqueId();

        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            if (user == null) {
                CompletableFuture<User> future = luckPerms.getUserManager().loadUser(uuid);
                user = future.get(3, TimeUnit.SECONDS);
            }
            if (user == null) return false;

            Tristate result = user.getCachedData().getPermissionData().checkPermission(permission);
            return result.asBoolean();
        } catch (TimeoutException e) {
            plugin.getLogger().warning("Délai dépassé en interrogeant LuckPerms pour " + target.getName() + " (permission " + permission + ").");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la vérification LuckPerms pour " + target.getName() + " : " + e.getMessage());
            return false;
        }
    }
}
