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
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class ServerFest extends JavaPlugin {

    public static final boolean supportsChatObjects = checkChatObjectSupport();
    public static final Component CHAT_PREFIX = buildChatPrefix();

    public static ArenaManager arenaManager;
    private Economy economy;

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

        if(setupEconomy()){
            getLogger().info("Hook into Vault has been established");
        }

        new UpdateChecker(this).check();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Stopping all ongoing fests...");
        FestManager.getInstance().getFests().forEach(Fest::stop);
    }

    //region Chat Object Support

    private static boolean checkChatObjectSupport() {
        String version = Bukkit.getMinecraftVersion();
        String[] parts = version.split("[.\\-]");

        int major = parsePart(parts, 0);
        int minor = parsePart(parts, 1);
        int patch = parsePart(parts, 2);

        // New year-based scheme (26.x and later): always supported
        if (major > 1) return true;

        // Legacy 1.x scheme: needs 1.21.9 or higher
        if (major == 1) {
            if (minor > 21) return true;
            return minor == 21 && patch >= 9;
        }

        return false;
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    //endregion

    private static Component buildChatPrefix() {
        Component icon = supportsChatObjects
                ? MiniMessage.miniMessage()
                .deserialize("<sprite:\"minecraft:items\":item/firework_rocket>")
                .color(NamedTextColor.WHITE)
                : Component.text("SF", NamedTextColor.GREEN);

        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(icon)
                .append(Component.text("] ", NamedTextColor.DARK_GRAY));
    }

    public static Component withChatPrefix(Component component){
        return CHAT_PREFIX.append(component);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public @Nullable Economy getEconomy() {
        return economy;
    }

    public boolean hasEconomy() {
        return economy != null;
    }

//    @EventHandler
//    public void onPlayerJoin(PlayerJoinEvent event){
//        event.getPlayer().teleport(WorldManager.getInstance().getHubWorld().getSpawnLocation());
//    }
}
