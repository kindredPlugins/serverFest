package at.gaderman.serverFest.compatibility;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

public final class GameRuleCompat {

    private static final Logger LOGGER = Logger.getLogger("GameRuleCompat");

    public static final Rule<Boolean> SPAWN_MOBS =
            resolve("SPAWN_MOBS", "DO_MOB_SPAWNING", "doMobSpawning");
    public static final Rule<Boolean> BLOCK_DROPS =
            resolve("BLOCK_DROPS", "DO_TILE_DROPS", "doTileDrops");
    public static final Rule<Boolean> DAYLIGHT_CYCLE =
            resolve("ADVANCE_TIME", "DO_DAYLIGHT_CYCLE", "doDaylightCycle");
    public static final Rule<Boolean> KEEP_INVENTORY =
            resolve("KEEP_INVENTORY", "KEEP_INVENTORY", "keepInventory");

    private GameRuleCompat() {}

    public static final class Rule<T> {
        private final String name;
        private final @Nullable GameRule<T> handle;

        private Rule(String name, @Nullable GameRule<T> handle) {
            this.name = name;
            this.handle = handle;
        }

        public boolean isSupported() {
            return handle != null;
        }

        /** @return true if the rule exists on this server and was applied */
        public boolean set(World world, T value) {
            if (handle == null) return false;
            return world.setGameRule(handle, value);
        }

        public @Nullable T get(World world) {
            return handle == null ? null : world.getGameRuleValue(handle);
        }

        @Override
        public String toString() {
            return name + (handle == null ? " (unsupported)" : "");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Rule<T> resolve(String newField, String oldField, String legacyName) {
        try {
            Class<?> rules = Class.forName("org.bukkit.GameRules");
            return new Rule<>(newField, (GameRule<T>) rules.getField(newField).get(null));
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
        }

        try {
            return new Rule<>(oldField, (GameRule<T>) GameRule.class.getField(oldField).get(null));
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
        }

        try {
            @SuppressWarnings("removal")
            GameRule<T> byName = (GameRule<T>) GameRule.getByName(legacyName);
            if (byName != null) return new Rule<>(legacyName, byName);
        } catch (Throwable ignored) {
        }

        LOGGER.warning("Game rule not available on this server version: " + newField + " / " + oldField);
        return new Rule<>(newField, null);
    }
}
