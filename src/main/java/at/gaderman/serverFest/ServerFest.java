package at.gaderman.serverFest;

import at.gaderman.serverFest.command.FestCommand;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestManager;
import at.gaderman.serverFest.fests.SaveStateManagement;
import at.gaderman.serverFest.fests.arenas.ArenaManager;
import at.gaderman.serverFest.fests.schedule.FestCalendar;
import at.gaderman.serverFest.management.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerFest extends JavaPlugin {

    public static final Component CHAT_PREFIX = Component.text("[", NamedTextColor.GREEN)
            .append(MiniMessage.miniMessage().deserialize("<sprite:\"minecraft:items\":item/firework_rocket>").color(NamedTextColor.WHITE))
            //.append(Component.text("SF", NamedTextColor.GREEN))
            .append(Component.text("] ", NamedTextColor.GREEN));

    public static ArenaManager arenaManager;

    public static ServerFest getPlugin() {
        return getPlugin(ServerFest.class);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic

        WorldManager.getInstance().setUpHub();
        WorldManager.getInstance().loadOrCreateFestWorld();

        getCommand("fest").setExecutor(new FestCommand());

        Bukkit.getPluginManager().registerEvents(new SaveStateManagement.SaveListener(), this);

        FestManager.getInstance();
        arenaManager = new ArenaManager();
        FestCalendar.getInstance().load();
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
