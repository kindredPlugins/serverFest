package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.config.GeneralConfig;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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

    private LocalDateTime timeStamp;
    private ChronoUnit timeUnit;
    private long offset;

    private ScheduledTask scheduledTask;

    private RecurringDateScheduled(LocalDateTime timeStamp, ChronoUnit timeUnit, long offset) {
       this(FestType.CASUAL, null, 2, GeneralConfig.getInstance().maxPlayersPerFest(), timeStamp, timeUnit, offset);
    }

    private RecurringDateScheduled(FestType festType, GameType gameType, int minPlayers, int maxPlayers, LocalDateTime timeStamp, ChronoUnit timeUnit, long offset) {
        super(festType, gameType, minPlayers, maxPlayers);
        this.timeStamp = timeStamp;
        this.timeUnit = timeUnit;
        this.offset = offset;
    }

    private RecurringDateScheduled(UUID uuid, FestType festType, GameType gameType, int minPlayers, int maxPlayers, LocalDateTime timeStamp, ChronoUnit timeUnit, long offset) {
        super(uuid, festType, gameType, minPlayers, maxPlayers);
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

    /**
     * Creates a new instance of this object using {@link ScheduledFest#create(Supplier)}
     */
    public static RecurringDateScheduled create(FestType festType, GameType gameType, int minPlayers, int maxPlayers, LocalDateTime timeStamp, ChronoUnit timeUnit, long offset) {
        return ScheduledFest.create(() -> new RecurringDateScheduled(festType, gameType, minPlayers, maxPlayers, timeStamp, timeUnit, offset));
    }

    @Override
    public void schedule() {
        LocalDateTime nextTime = nextSchedule();

        long offset = LocalDateTime.now().until(nextTime, ChronoUnit.SECONDS) * 20;
        scheduledTask = Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            startUp();

            Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                schedule();
            }, 20);
        }, Math.max(offset, 0));
        //TODO: check if there is a possible issue where offset could go < 0, cause this is just a plaster
    }

    /**
     * Calculates the next time this fest will occur, using this is preferred over timestamp as this will accurately
     * move timestamp to a position within the offset.
     */
    @Override
    public LocalDateTime nextSchedule() {
        LocalDateTime current = LocalDateTime.now();

        // this would move a future repeating towards now, but maybe someone wants the repetition to only start after a specific date
//        while (current.until(timeStamp, timeUnit) > offset)
//            timeStamp = timeStamp.minus(offset, timeUnit);

        while (timeStamp.isBefore(current))
            timeStamp = timeStamp.plus(offset, timeUnit);

        return timeStamp;
    }

    @Override
    public void cancel() {
        if (scheduledTask != null)
            scheduledTask.cancel();
    }

    @Override
    protected void writeExtraConfig(ConfigurationSection config) {
        config.set("timeStamp", timeStamp.toString());
        config.set("timeUnit", timeUnit().toString().toUpperCase());
        config.set("offset", offset);
    }

    public static RecurringDateScheduled deserialize(ConfigurationSection config, UUID uuid, FestType festType,
                                                     @Nullable GameType gameType, int minPlayers, int maxPlayers) {
        LocalDateTime timeStamp = LocalDateTime.parse(config.getString("timeStamp"));
        ChronoUnit timeUnit = ChronoUnit.valueOf(config.getString("timeUnit"));
        long offset = config.getLong("offset");

        return new RecurringDateScheduled(uuid, festType, gameType, minPlayers, maxPlayers, timeStamp, timeUnit, offset);
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
