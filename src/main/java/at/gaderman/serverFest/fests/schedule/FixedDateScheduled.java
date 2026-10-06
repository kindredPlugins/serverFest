package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A fest scheduled for a fixed date once, after this fest was scheduled at this point it will remove itself from
 * the calendar
 */
public class FixedDateScheduled extends ScheduledFest {

    private LocalDateTime timeStamp;

    private FixedDateScheduled(@Nullable GameType gameType, LocalDateTime timeStamp) {
        super();
        setGameType(gameType);
        this.timeStamp = timeStamp;
    }

    private FixedDateScheduled(FestType festType, @Nullable GameType gameType, int minPlayers, int maxPlayers, LocalDateTime timeStamp) {
        super(festType, gameType, minPlayers, maxPlayers);
        this.timeStamp = timeStamp;
    }

    private FixedDateScheduled(UUID uuid, FestType festType, @Nullable GameType gameType, int minPlayers, int maxPlayers, LocalDateTime timeStamp) {
        super(uuid, festType, gameType, minPlayers, maxPlayers);
        this.timeStamp = timeStamp;
    }

    /**
     * Creates a new instance of this object using {@link ScheduledFest#create(Supplier)}
     */
    public static FixedDateScheduled create(LocalDateTime timeStamp) {
        return ScheduledFest.create(() -> new FixedDateScheduled(null, timeStamp));
    }

    public static FixedDateScheduled create(FestType festType, @Nullable GameType gameType, int minPlayers, int maxPlayers, LocalDateTime timeStamp) {
        return ScheduledFest.create(() -> new FixedDateScheduled(festType, gameType, minPlayers, maxPlayers, timeStamp));
    }

    @Override
    public void schedule() {
        LocalDateTime now = LocalDateTime.now();

        if (now.isAfter(timeStamp)) {
            FestCalendar.getInstance().removeFestEntry(uuid());
            return;
        }

        long offset = now.until(timeStamp, ChronoUnit.SECONDS) * 20;
        Bukkit.getScheduler().runTaskLater(ServerFest.getPlugin(), () -> {
            startUp();
            FestCalendar.getInstance().removeFestEntry(uuid());
        }, offset);
    }

    @Override
    protected void writeExtraConfig(ConfigurationSection config) {
        config.set("timeStamp", timeStamp.toString());
    }

    public static FixedDateScheduled deserialize(ConfigurationSection config, UUID uuid, FestType festType,
                                                 @Nullable GameType gameType, int minPlayers, int maxPlayers) {
        LocalDateTime timeStamp = LocalDateTime.parse(config.getString("timeStamp"));
        return new FixedDateScheduled(uuid, festType, gameType, minPlayers, maxPlayers, timeStamp);
    }

    public LocalDateTime timeStamp() {
        return timeStamp;
    }

    public FixedDateScheduled setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
        return this;
    }
}
