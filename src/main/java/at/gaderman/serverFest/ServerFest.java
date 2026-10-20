package at.gaderman.serverFest;

import at.gaderman.serverFest.command.FestCommand;
import at.gaderman.serverFest.config.GeneralConfig;
import at.gaderman.serverFest.data.DataConfig;
import at.gaderman.serverFest.data.player.PlayerDataManager;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestManager;
import at.gaderman.serverFest.fests.SaveStateManagement;
import at.gaderman.serverFest.fests.arenas.ArenaManager;
import at.gaderman.serverFest.fests.rewards.ChampionTitleManager;
import at.gaderman.serverFest.fests.schedule.FestCalendar;
import at.gaderman.serverFest.libs.PAPIExpansion;
import at.gaderman.serverFest.management.WorldManager;
import at.gaderman.serverFest.utils.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerFest extends JavaPlugin {

    public static final Component CHAT_PREFIX = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(MiniMessage.miniMessage().deserialize("<sprite:\"minecraft:items\":item/firework_rocket>").color(NamedTextColor.WHITE))
            //.append(Component.text("SF", NamedTextColor.GREEN))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY));

    public static ArenaManager arenaManager;

    public static ServerFest getPlugin() {
        return getPlugin(ServerFest.class);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        GeneralConfig.getInstance();
        DataConfig.getInstance();

        WorldManager.getInstance().loadOrCreateFestWorld();

        getCommand("fest").setExecutor(new FestCommand());

        Bukkit.getPluginManager().registerEvents(new SaveStateManagement.SaveListener(), this);
        Bukkit.getPluginManager().registerEvents(ChampionTitleManager.getInstance(), this);
        Bukkit.getPluginManager().registerEvents(PlayerDataManager.getInstance(), this);

        FestManager.getInstance();
        arenaManager = new ArenaManager();
        FestCalendar.getInstance().load();

        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){
            if(!new PAPIExpansion(this).register())
                getLogger().warning("Could not register PlaceholderAPI expansion");
        }

        new UpdateChecker(this).check();
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
