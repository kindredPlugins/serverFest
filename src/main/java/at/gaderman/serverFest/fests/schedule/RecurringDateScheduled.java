package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.config.GeneralConfig;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * A fest scheduled around a specific point in time with a specific offset. Upon fest initialization will automatically
 * schedule itself again using the offset on the timestamp.
 */
public class RecurringDateScheduled extends ScheduledFest {

    private LocalDateTime timeStamp;
    private ChronoUnit timeUnit;
    private long offset;

    private volatile ScheduledTask scheduledTask;
    private final AtomicInteger generation = new AtomicInteger();

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
        cancel();
        if (offset <= 0) {
            ServerFest.getPlugin().getLogger().warning("Recurring fest has an invalid offset, not scheduling it");
            return;
        }
        scheduleNext(LocalDateTime.now(), generation.get());
    }

    /**
     * Trys to publish a schedule, this will only wake up once every minute to check, since otherwise ticks could grow
     * too large or be disruptet by server tps mismatch
     */
    private void scheduleNext(LocalDateTime from, int chain) {
        if (chain != generation.get()) return;

        LocalDateTime target = occurrenceAtOrAfter(from);
        long millis = Math.max(0, Duration.between(LocalDateTime.now(), target).toMillis());
        long ticks = Math.clamp((long) Math.ceil(millis / 50.0), 1, 20 * 60);

        scheduledTask = Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            if (chain != generation.get()) return;

            if (LocalDateTime.now().isBefore(target)) {
                scheduleNext(target, chain);
                return;
            }

            try {
                startUp();
            } catch (Exception e) {
                ServerFest.getPlugin().getLogger().log(Level.SEVERE, "Failed to start recurring fest", e);
            } finally {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime after = target.plusNanos(1);
                scheduleNext(now.isAfter(after) ? now : after, chain);
            }
        }, ticks);
    }

    /** First occurrence that is not before {@code from}. Pure: does not modify timeStamp. */
    private LocalDateTime occurrenceAtOrAfter(LocalDateTime from) {
        if (!timeStamp.isBefore(from)) return timeStamp;

        long steps = Math.max(0, timeUnit.between(timeStamp, from) / offset);
        LocalDateTime candidate = timeStamp.plus(steps * offset, timeUnit);
        while (candidate.isBefore(from)) {
            steps++;
            candidate = timeStamp.plus(steps * offset, timeUnit);
        }
        return candidate;
    }

    @Override
    public LocalDateTime nextSchedule() {
        return occurrenceAtOrAfter(LocalDateTime.now());
    }

    @Override
    public void cancel() {
        generation.incrementAndGet();
        ScheduledTask task = scheduledTask;
        if (task != null) task.cancel();
    }

    @Override
    protected void writeExtraConfig(ConfigurationSection config) {
        config.set("timeStamp", timeStamp.toString());
        config.set("timeUnit", timeUnit().name());
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
