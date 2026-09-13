package at.gaderman.serverFest;

import at.gaderman.serverFest.command.FestCommand;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestManager;
import at.gaderman.serverFest.management.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerFest extends JavaPlugin implements Listener {

    public static final Component CHAT_PREFIX = Component.text("[ServerFest] ", NamedTextColor.GREEN);

    public static ServerFest getPlugin(){
        return getPlugin(ServerFest.class);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic

        WorldManager.getInstance().setUpHub();
        WorldManager.getInstance().loadOrCreateFestWorld();

        getCommand("fest").setExecutor(new FestCommand());

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Stopping all ongoing fests...");
        FestManager.getInstance().getFests().forEach(Fest::stop);
    }

//    @EventHandler
//    public void onPlayerJoin(PlayerJoinEvent event){
//        event.getPlayer().teleport(WorldManager.getInstance().getHubWorld().getSpawnLocation());
//    }
}
