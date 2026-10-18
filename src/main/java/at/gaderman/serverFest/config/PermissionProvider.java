package at.gaderman.serverFest.config;

import org.bukkit.entity.Player;

public class PermissionProvider {

    public static boolean canManageFests(Player player) {
        return player.hasPermission("serverFest.fest.manage");
    }

}
