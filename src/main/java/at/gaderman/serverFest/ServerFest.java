package at.gaderman.serverFest;

import at.gaderman.serverFest.management.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerFest extends JavaPlugin implements Listener {

    public static ServerFest getPlugin(){
        return getPlugin(ServerFest.class);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic

        WorldManager.getInstance().setUpHub();

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        event.getPlayer().teleport(WorldManager.getInstance().getHubWorld().getSpawnLocation());
    }
}
