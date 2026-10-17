package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Holds all {@link ScheduledFest} instances, manages the config communication for them
 */
public class FestCalendar {
    private static FestCalendar instance;

    public static FestCalendar getInstance() {
        if (instance == null) instance = new FestCalendar();
        return instance;
    }

    private final Map<UUID, ScheduledFest> calendar = new LinkedHashMap<>();

    private final File file;
    private final YamlConfiguration config;

    private FestCalendar() {
        file = new File(ServerFest.getPlugin().getDataFolder().getPath() + "/scheduled-fests.yml");
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Adds the given fest to the calendar and writes it to config
     */
    public void addFestEntry(ScheduledFest fest) {
        calendar.put(fest.uuid(), fest);
        ConfigurationSection festSection = config.createSection("fests." + fest.uuid());
        fest.writeToConfig(festSection);

        save();
    }

    public void removeFestEntry(UUID uuid) {
        ScheduledFest fest = calendar.remove(uuid);
        fest.cancel();
        config.set("fests." + uuid, null);

        save();
    }

    private void save() {
        try {
            config.save(file);
        }catch (IOException exception){
            ServerFest.getPlugin().getLogger().warning("Could not save scheduled-fests.yml");
        }
    }

    /**
     * Reads all fests from the config file and adds them into the active calendar
     */
    public void load(){
        ConfigurationSection festSection = config.getConfigurationSection("fests.");
        if(festSection == null) return;

        festSection.getKeys(false).forEach(key -> {
            ScheduledFest fest = ScheduledFest.deserialize(festSection.getConfigurationSection(key));
            addFestEntry(fest);
            fest.schedule();
        });

        ServerFest.getPlugin().getLogger().info("Scheduled fests have been loaded");
    }

    public List<ScheduledFest> calendar(){
        return calendar.values().stream()
                .sorted(Comparator.comparing(ScheduledFest::nextSchedule))
                .toList();
    }
}
