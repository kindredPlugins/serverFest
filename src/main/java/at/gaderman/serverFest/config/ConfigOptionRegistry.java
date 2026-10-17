package at.gaderman.serverFest.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A small registry helper class used to quickly access and expose config values, manages configOptions easily
 */
public class ConfigOptionRegistry {

    private final YamlConfiguration config;
    private final @Nullable String prefix;

    private final Map<String, ConfigOption<?>> configOptions = new LinkedHashMap<>();

    public ConfigOptionRegistry(YamlConfiguration config) {
        this(config, null);
    }

    public ConfigOptionRegistry(YamlConfiguration config, @Nullable String prefix) {
        this.config = config;
        this.prefix = prefix;
    }

    protected <T> ConfigOption<T> configOption(String id, T defaultValue, ConfigOption.ConfigReader<T> reader) {
        return configOption(id, defaultValue, reader, value -> value);
    }

    public <T> ConfigOption<T> configOption(String id, T defaultValue, ConfigOption.ConfigReader<T> reader, Function<T, T> valueFunction) {
        return configOption(id, defaultValue, reader, valueFunction, Object::toString);
    }

    public <T> ConfigOption<T> configOption(String id, T defaultValue, ConfigOption.ConfigReader<T> reader, Function<T, T> valueFunction, Function<T, String> displayFunction) {
        ConfigOption<T> option = new ConfigOption<>(id, config, prefix, defaultValue, reader, valueFunction, displayFunction);
        configOptions.put(id, option);
        return option;
    }

    public Map<String, ConfigOption<?>> configOptions() {
        return Collections.unmodifiableMap(configOptions);
    }

    public void clearConfigOptions(){
        configOptions.clear();
    }

    public void reloadConfig() {
        configOptions.values().forEach(ConfigOption::reloadFromConfig);
    }
}
