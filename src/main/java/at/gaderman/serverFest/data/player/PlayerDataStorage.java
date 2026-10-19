package at.gaderman.serverFest.data.player;

import at.gaderman.serverFest.ServerFest;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerDataStorage {

    private static final PlayerDataStorage INSTANCE = new PlayerDataStorage();

    public static PlayerDataStorage getInstance() {
        return INSTANCE;
    }

    public static final String FESTS_PARTICIPATED = "fests_participated";
    public static final String FESTS_WON = "fests_won";
    public static final String DUELS_HELD = "duels_held";
    public static final String DUELS_WON = "duels_won";
    public static final String CHAMPION_TIMES = "champion_times";

    private PlayerDataStorage() {
    }

    private File fileOf(UUID uuid) {
        return new File(new File(ServerFest.getPlugin().getDataFolder(), "playerdata"), uuid + ".yml");
    }

    public PlayerData readPlayerData(UUID uuid) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(fileOf(uuid));

        return new PlayerData(
                config.getInt(FESTS_PARTICIPATED, 0),
                config.getInt(FESTS_WON, 0),
                config.getInt(DUELS_HELD, 0),
                config.getInt(DUELS_WON, 0),
                config.getInt(CHAMPION_TIMES, 0)
        );
    }

    public void savePlayerData(UUID uuid, PlayerData data) {
        Bukkit.getAsyncScheduler().runNow(ServerFest.getPlugin(), _ -> {
            YamlConfiguration config = new YamlConfiguration();

            config.set("data-version", 1);
            config.set(FESTS_PARTICIPATED, data.festsParticipated());
            config.set(FESTS_WON, data.festsWon());
            config.set(DUELS_HELD, data.duelsHeld());
            config.set(DUELS_WON, data.duelsWon());
            config.set(CHAMPION_TIMES, data.championTimes());

            File file = fileOf(uuid);

            try {
                Files.createDirectories(file.toPath().getParent());
                config.save(file);
            } catch (IOException exception) {
                ServerFest.getPlugin().getLogger().log(Level.SEVERE, "Could not save player data for " + uuid, exception);
            }
        });
    }
}