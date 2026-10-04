package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.game.GameType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A fest scheduled around a specific point in time with a specific offset. Upon fest initialization will automatically
 * schedule itself again using the offset on the timestamp.
 */
public class RecurringDateScheduled extends ScheduledFest {

    private LocalDateTime timeStamp = LocalDateTime.now().plusSeconds(5);
    private ChronoUnit timeUnit = ChronoUnit.WEEKS;
    private long offset;

    private RecurringDateScheduled(LocalDateTime timeStamp, ChronoUnit timeUnit, long offset) {
        super();
        this.timeStamp = timeStamp;
        this.timeUnit = timeUnit;
        this.offset = offset;
    }

    private RecurringDateScheduled(UUID uuid, GameType gameType, int minPlayers, LocalDateTime timeStamp, ChronoUnit timeUnit, long offset) {
        super(uuid, gameType, minPlayers);
        this.timeStamp = timeStamp;
        this.timeUnit = timeUnit;
        this.offset = offset;
    }

    /**
     * Creates a new instance of this object using {@link ScheduledFest#create(Supplier)}
     */
    public static RecurringDateScheduled create(LocalDateTime timeStamp, ChronoUnit timeUnit, long offset) {
        return ScheduledFest.create(() -> new RecurringDateScheduled(timeStamp, timeUnit, offset));
    }

    @Override
    public void schedule() {
        LocalDateTime nextTime = nextSchedule();

        long offset = LocalDateTime.now().until(nextTime, ChronoUnit.SECONDS) * 20;
        Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            startUp();

            Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                schedule();
            }, 20);
        }, offset);
    }

    /**
     * Calculates the next time this fest will occur, using this is preferred over timestamp as this will accurately
     * move timestamp to a position within the offset.
     */
    public LocalDateTime nextSchedule() {
        LocalDateTime current = LocalDateTime.now();

        while (current.until(timeStamp, timeUnit) > offset)
            timeStamp = timeStamp.minus(offset, timeUnit);

        while (timeStamp.isBefore(current))
            timeStamp = timeStamp.plus(offset, timeUnit);

        return timeStamp;
    }

    @Override
    protected void writeExtraConfig(ConfigurationSection config) {
        config.set("timeStamp", timeStamp.toString());
        config.set("timeUnit", timeUnit().toString().toUpperCase());
        config.set("offset", offset);
    }

    public static RecurringDateScheduled deserialize(ConfigurationSection config, UUID uuid, @Nullable GameType gameType, int minPlayers) {
        LocalDateTime timeStamp = LocalDateTime.parse(config.getString("timeStamp"));
        ChronoUnit timeUnit = ChronoUnit.valueOf(config.getString("timeUnit"));
        long offset = config.getLong("offset");

        return new RecurringDateScheduled(uuid, gameType, minPlayers, timeStamp, timeUnit, offset);
    }

    public LocalDateTime timeStamp() {
        return timeStamp;
    }

    public RecurringDateScheduled setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
        return this;
    }

    public ChronoUnit timeUnit() {
        return timeUnit;
    }

    public RecurringDateScheduled setTimeUnit(ChronoUnit timeUnit) {
        this.timeUnit = timeUnit;
        return this;
    }

    public long offset() {
        return offset;
    }

    public RecurringDateScheduled setOffset(long offset) {
        this.offset = offset;
        return this;
    }
}
