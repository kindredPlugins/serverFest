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
        ServerFest.getPlugin().getLogger().info("config.yml has been loaded");
    }

    public int arenaSlots() {
        return ARENA_SLOTS.cached();
    }

    public int maxPlayersPerFest() {
        return MAX_FEST_PLAYERS.cached();
    }

    public boolean useWorldSeed() {
        return USE_WORLD_SEED.cached();
    }

    public int maxFestDuration() {
        return MAX_FEST_DURATION.cached();
    }

    private final ConfigOption<Integer> ARENA_SLOTS = configRegistry.configOption("arenas.slots", 3, FileConfiguration::getInt, value -> Math.clamp(value, 1, 30))
            .withComments("Amount of arena slots generated and reserved per game type.",
                    "Especially important if your players host many duels, as too few arenas lead to waiting times.",
                    "CAREFUL, each arena slot has to be generated, so more arenas equal to more startup pressure for your server.",
                    "The set maximum is 30");
    private final ConfigOption<Integer> MAX_FEST_PLAYERS = configRegistry.configOption("fests.participants.max", 99, FileConfiguration::getInt, value -> Math.clamp(value, 2, 500))
            .withComments("Limit to the amount of max players a fest can be configured to allow.",
                    "You can change the max player count individually for each fest, this just represents the upper limit which is possible with this setting.",
                    "Note this is mostly based on your server. Fests try to be performative but many players are an issue for Minecraft servers.",
                    "Try to create most fests with a reasonable amount to keep them playable, but if you want you can turn this value up to 500.",
                    "You have been warned"
            );
    private final ConfigOption<Boolean> USE_WORLD_SEED = configRegistry.configOption("fests.world.use_world_seed", true, FileConfiguration::getBoolean)
            .withComments("If set to true the generated fest world will use the same seed as the main world.",
                    "This means even if you delete the fest world it will generate with the same seed again.",
                    "If you periodically delete your fest world and want arenas to change, set this to false");
    private final ConfigOption<Integer> MAX_FEST_DURATION = configRegistry.configOption("fests.max_duration", 5 * 60, FileConfiguration::getInt, value -> Math.max(value, 60))
            .withComments("Maximum amount of time a fest can run before being terminated.",
                    "Used to disable a case of someone AFKing during a fest and blocking the arena for others.",
                    "At least 1 minute since fests cannot be realistically be completed otherwise.",
                    "Time given in seconds.");
}
