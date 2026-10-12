package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.recruitment.FestRecruitment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Represents a fest, registered in the {@link FestCalendar}, upon reaching its condition it will create a new fest
 * instance and so lead to a real fest starting, until then it's just a mark in a calendar.
 */
public abstract class ScheduledFest {

    public static final int NO_LIMIT_MAX_PLAYERS = -1;

    private final UUID uuid;
    private FestType festType;
    private @Nullable GameType gameType;
    protected int minPlayers;
    protected int maxPlayers;

    protected ScheduledFest() {
        //TODO: min fest players should always be 2, the special case is a fest dev-test (/fest dev) which should allow 1 person as it is just for testing
        this(UUID.randomUUID(), FestType.CASUAL, null, 1, NO_LIMIT_MAX_PLAYERS);
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

//        Fest festInstance = new Fest(festType, instanceType, new ArrayList<>());

//        Bukkit.getOnlinePlayers().forEach(player -> {
//            player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.65f);
//
//            player.sendTitlePart(TitlePart.TITLE, festInstance.game().gameType().definition().iconName());
//            player.sendTitlePart(TitlePart.SUBTITLE, Component.text("will start in 5 minutes", NamedTextColor.YELLOW));
//        });
//
//        final int[] timeLeft = {6}; // 30 seconds
//        sendStartInMessage(timeLeft[0]);
//
//        new BukkitRunnable() {
//            @Override
//            public void run() {
//                timeLeft[0]--;
//
//                if (timeLeft[0] / 60 > 0) {
//                    if (timeLeft[0] % 60 == 0)
//                        sendStartInMessage(timeLeft[0]);
//                } else {
//                    if (timeLeft[0] % 15 == 0 || timeLeft[0] == 10 || timeLeft[0] <= 5)
//                        sendStartInMessage(timeLeft[0]);
//
//                    if (timeLeft[0] <= 0) {
//                        cancel();
//                        festInstance.start();
//                    }
//                }
//            }
//        }.runTaskTimer(ServerFest.getPlugin(), 20L, 20L);
    }

    private void sendStartInMessage(int timeLeft) {
        int minutes = timeLeft / 60;
        int seconds = timeLeft % 60;

        Bukkit.getOnlinePlayers().forEach(player -> {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(displayName())
                    .append(Component.text(" is starting in ", NamedTextColor.WHITE))
                    .append(Component.text((minutes > 0 ? minutes : seconds), NamedTextColor.GRAY))
                    .append(Component.text((minutes > 0 ? " minutes(s)" : " second(s)"), NamedTextColor.WHITE))
            );
        });
    }

    public Component displayName() {
        return Component.text(toPlayerFriendlyName(festType.name()) + " "
                + (gameType == null ? "Random Fest" : toPlayerFriendlyName(gameType.name())), NamedTextColor.GOLD);
    }

    private static String toPlayerFriendlyName(String input) {
        StringBuilder builder = new StringBuilder();

        boolean bigLetter = true;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '_') {
                bigLetter = true;
                builder.append(' ');
                continue;
            }

            if (bigLetter) {
                builder.append((c + "").toUpperCase());
                bigLetter = false;
            } else {
                builder.append((c + "").toLowerCase());
            }
        }

        return builder.toString();
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
        int maxPlayers = config.getInt("maxPlayers", NO_LIMIT_MAX_PLAYERS);

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
