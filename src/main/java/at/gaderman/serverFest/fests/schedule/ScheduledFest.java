package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.config.GeneralConfig;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.recruitment.FestRecruitment;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Represents a fest, registered in the {@link FestCalendar}, upon reaching its condition it will create a new fest
 * instance and so lead to a real fest starting, until then it's just a mark in a calendar.
 */
public abstract class ScheduledFest {

    private final UUID uuid;
    private FestType festType;
    private @Nullable GameType gameType;
    protected int minPlayers;
    protected int maxPlayers;

    protected ScheduledFest() {
        //TODO: min fest players should theoretically always be 2, the special case is a fest dev-test (/fest dev) which should allow 1 person as it is just for testing or a competition when everyone sleeps
        this(UUID.randomUUID(), FestType.CASUAL, null, 1, GeneralConfig.getInstance().maxPlayersPerFest());
    }

    protected ScheduledFest(@Nullable GameType gameType, int minPlayers, int maxPlayers) {
        this(UUID.randomUUID(), FestType.CASUAL, gameType, minPlayers, maxPlayers);
    }

    protected ScheduledFest(FestType festType, @Nullable GameType gameType, int minPlayers, int maxPlayers) {
        this(UUID.randomUUID(), festType, gameType, minPlayers, maxPlayers);
    }

    protected ScheduledFest(UUID uuid, FestType festType, @Nullable GameType gameType, int minPlayers, int maxPlayers) {
        this.uuid = uuid;
        this.festType = festType;
        this.gameType = gameType;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    /**
     * Creates a ScheduledFest given the Supplier, automatically registers on the {@link FestCalendar} and calls
     * {@link #schedule()}.
     *
     * @see FixedDateScheduled#create(Supplier)
     * @see RecurringDateScheduled#create(Supplier)
     */
    public static <T extends ScheduledFest> T create(Supplier<T> constructor) {
        T fest = constructor.get();
        FestCalendar.getInstance().addFestEntry(fest);
        fest.schedule();
        return fest;
    }

    /**
     * Sets up the schedule for this event
     */
    public abstract void schedule();

    /**
     * Returns the upcoming time this fest is scheduled to occur.
     */
    public abstract LocalDateTime nextSchedule();

    /**
     * Cancels the fest from being scheduled, important to call this as this will stop the schedulers
     */
    public abstract void cancel();

    /**
     * Kicks off the five minutes starting-sequence
     */
    public void startUp() {
        GameType instanceType = gameType;
        if (gameType == null) {
            instanceType = GameType.values()[(int) (Math.random() * GameType.values().length)];
        }

        if (ServerFest.arenaManager.requestArena(instanceType) == null) {
            Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                startUp();
            }, 20);
            return;
        }

        //TODO: inspect this properly, make a fixed constant for time left, this way fests still actually occur one minute late, may adjust it to trigger one minute before
        FestRecruitment.requestRecruitment(60, festType, instanceType);
    }

    /**
     * Writes fields of this scheduled fest into the given config section
     *
     * @see ScheduledFest#writeExtraConfig(ConfigurationSection)
     */
    public final void writeToConfig(ConfigurationSection config) {
        config.set("festType", festType.toString().toUpperCase());
        if (gameType != null)
            config.set("gameType", gameType.toString().toUpperCase());
        config.set("minPlayers", minPlayers);
        config.set("maxPlayers", maxPlayers);

        ConfigurationSection scheduleSection = config.getConfigurationSection("schedule");
        if (scheduleSection == null)
            scheduleSection = config.createSection("schedule");

        scheduleSection.set("type", this.getClass().equals(FixedDateScheduled.class) ? "fixedDate" : "recurring");
        writeExtraConfig(scheduleSection);
    }

    /**
     * Writes extra fields of the unique {@link ScheduledFest} implementation to the given config section (usually already
     * .schedule sub-section)
     */
    protected abstract void writeExtraConfig(ConfigurationSection config);

    /**
     * Reads a config section and transforms it back into a ScheduledFest, the name of the section should be equal
     * to the {@link ScheduledFest#uuid}, .schedule.type defines the concrete implementation
     *
     * @see FixedDateScheduled#deserialize(ConfigurationSection)
     * @see RecurringDateScheduled#deserialize(ConfigurationSection)
     */
    public static ScheduledFest deserialize(ConfigurationSection config) {
        String type = config.getString("schedule.type");

        UUID uuid = UUID.fromString(config.getName());
        FestType festType = FestType.valueOf(config.getString("festType"));
        String gameTypeString = config.getString("gameType", null);
        GameType gameType = gameTypeString == null ? null : GameType.valueOf(gameTypeString);
        int minPlayers = config.getInt("minPlayers", 2);
        int maxPlayers = config.getInt("maxPlayers", GeneralConfig.getInstance().maxPlayersPerFest());

        ConfigurationSection scheduleSection = config.getConfigurationSection("schedule");

        if (scheduleSection == null)
            throw new IllegalArgumentException("Invalid schedule fest type at fest " + uuid);

        return switch (type) {
            case "fixedDate" ->
                    FixedDateScheduled.deserialize(scheduleSection, uuid, festType, gameType, minPlayers, maxPlayers);
            case "recurring" ->
                    RecurringDateScheduled.deserialize(scheduleSection, uuid, festType, gameType, minPlayers, maxPlayers);
            default -> throw new IllegalArgumentException("Invalid scheduled fest type");
        };
    }

    public UUID uuid() {
        return uuid;
    }

    public FestType festType() {
        return festType;
    }

    public ScheduledFest setFestType(FestType festType) {
        this.festType = festType;
        return this;
    }

    public @Nullable GameType gameType() {
        return gameType;
    }

    public ScheduledFest setGameType(@Nullable GameType gameType) {
        this.gameType = gameType;
        return this;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public ScheduledFest setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
        return this;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public ScheduledFest setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledFest that = (ScheduledFest) o;
        return minPlayers == that.minPlayers && Objects.equals(uuid, that.uuid) && gameType == that.gameType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, minPlayers, gameType);
    }
}
