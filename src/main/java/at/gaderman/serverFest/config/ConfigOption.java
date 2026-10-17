package at.gaderman.serverFest.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public final class ConfigOption<T> {
    private final String id;
    private final YamlConfiguration config;
    private final @Nullable String prefix;
    private final T defaultValue;
    private volatile T cached;
    private final ConfigReader<T> reader;
    private final Function<T, T> valueFunction;
    private final Function<T, String> displayFunction;

    private @Nullable List<String> comments;

    @FunctionalInterface
    public interface ConfigReader<T> {
        T read(FileConfiguration config, String path, T def);
    }

    public ConfigOption(String id, YamlConfiguration config, @Nullable String prefix, T defaultValue, ConfigReader<T> reader) {
        this(id, config, prefix, defaultValue, reader, value -> value);
    }

    public ConfigOption(String id, YamlConfiguration config, @Nullable String prefix, T defaultValue, ConfigReader<T> reader, Function<T, T> valueFunction) {
        this(id, config, prefix, defaultValue, reader, valueFunction, Object::toString);
    }

    public ConfigOption(String id, YamlConfiguration config, @Nullable String prefix, T defaultValue, ConfigReader<T> reader, Function<T, T> valueFunction, Function<T, String> displayFunction) {
        this.id = id;
        this.config = config;
        this.prefix = prefix;
        this.defaultValue = defaultValue;
        this.reader = reader;
        this.valueFunction = valueFunction;
        this.displayFunction = displayFunction;

        reloadFromConfig();
    }

    public ConfigOption<T> withComments(String... comments) {
        this.comments = List.of(comments);
        config.setComments(id, this.comments);
        return this;
    }

    public void reloadFromConfig() {
        cached = valueFunction.apply(reader.read(config, resolvePrefix() + id, defaultValue));
    }

    public String id() {
        return id;
    }

    public T cached() {
        return cached;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public String displayValue() {
        return displayFunction.apply(cached);
    }

    public @Nullable List<String> comments() {
        return comments;
    }

    private String resolvePrefix() {
        return prefix == null ? "" : prefix + ".";
    }
}
