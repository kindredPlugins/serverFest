package at.gaderman.serverFest.config;

import at.gaderman.serverFest.ServerFest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class GeneralConfig {

    private static GeneralConfig instance;

    private GeneralConfig() {
        setUp();
    }

    public static GeneralConfig getInstance() {
        if (instance == null) instance = new GeneralConfig();
        return instance;
    }

    private final YamlConfiguration config = (YamlConfiguration) ServerFest.getPlugin().getConfig();
    private final ConfigOptionRegistry configRegistry = new ConfigOptionRegistry(config, "settings");

    private void setUp() {
        configRegistry.configOptions().forEach((key, value) -> {
            String setKey = "settings." + key;

            if (!config.contains(setKey))
                config.set(setKey, value.defaultValue());

            if (value.comments() != null)
                config.setComments(setKey, value.comments());
        });

        configRegistry.reloadConfig();
        ServerFest.getPlugin().saveConfig();
    }

    public final ConfigOption<Integer> ARENA_SLOTS = configRegistry.configOption("arenas.slots", 3, FileConfiguration::getInt, value -> Math.clamp(value, 1, 30))
            .withComments("Amount of arena slots generated and reserved per game type.",
                    "Especially important if your players host many duels, as too few arenas lead to waiting times.",
                    "CAREFUL, each arena slot has to be generated, so more arenas equal to more startup pressure for your server.",
                    "The set maximum is 30");
}
