package at.gaderman.serverFest.data;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.rewards.ChampionTitleManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DataConfig {

    private static DataConfig instance;

    public static DataConfig getInstance() {
        if (instance == null) instance = new DataConfig();
        return instance;
    }

    private final File file;
    private final YamlConfiguration config;

    private static final String COMPETITIVE_ROUND = "competitive-round";
    private static final String FESTS_TOTAL = "fests-held-total";
    private static final String CASUAL_HELD = "casual-fests-held";
    private static final String DUELS_HELD = "duels-held";

    private DataConfig() {
        file = new File(ServerFest.getPlugin().getDataFolder(), "data.yml");

        config = YamlConfiguration.loadConfiguration(file);
        ensureDefaults();
        save();
    }

    private void ensureDefaults() {
        config.addDefault(COMPETITIVE_ROUND, 0);
        config.addDefault(FESTS_TOTAL, 0);
        config.addDefault(CASUAL_HELD, 0);
        config.addDefault(DUELS_HELD, 0);
        config.setComments(CASUAL_HELD, List.of("DO NOT MODIFY ANYTHING IN HERE. THIS IS JUST A DATA STORAGE"));

        config.options().copyDefaults(true);
    }

    public int competitiveRound() {
        return config.getInt(COMPETITIVE_ROUND, 1);
    }

    public void setCompetitiveRound(int value) {
        config.set(COMPETITIVE_ROUND, value);
        ChampionTitleManager.getInstance().updateActiveChampion();
        save();
    }

    public int festsHeldTotal() {
        return config.getInt(FESTS_TOTAL, 0);
    }

    public void setFestsHeldTotal(int value) {
        config.set(FESTS_TOTAL, value);
        save();
    }

    public int casualFestsHeld() {
        return config.getInt(CASUAL_HELD, 0);
    }

    public void setCasualFestsHeld(int value) {
        config.set(CASUAL_HELD, value);
        save();
    }

    public int duelsHeld() {
        return config.getInt(DUELS_HELD, 0);
    }

    public void setDuelsHeld(int value) {
        config.set(DUELS_HELD, value);
        save();
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException exception) {
            ServerFest.getPlugin().getLogger().warning("Could not save data.yml");
        }
    }
}
