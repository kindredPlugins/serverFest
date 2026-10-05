package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.GameType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Represents a fest, registered in the {@link FestCalendar}, upon reaching its condition it will create a new fest
 * instance and so lead to a real fest starting, until then it's just a mark in a calendar.
 */
public abstract class ScheduledFest {

    private final UUID uuid;
    protected int minPlayers;
    private @Nullable GameType gameType;

    protected ScheduledFest(){
        //TODO: min fest players should always be 2, the special case is a fest dev-test (/fest dev) which should allow 1 person as it is just for testing
        this(UUID.randomUUID(), null, 1);
    }

    protected ScheduledFest(@Nullable GameType gameType, int minPlayers){
        this(UUID.randomUUID(), gameType, minPlayers);
    }

    protected ScheduledFest(UUID uuid, @Nullable GameType gameType, int minPlayers) {
        this.uuid = uuid;
        this.gameType = gameType;
        this.minPlayers = minPlayers;
    }

    /**
     * Creates a ScheduledFest given the Supplier, automatically registers on the {@link FestCalendar} and calls
     * {@link #schedule()}.
     * @see FixedDateScheduled#create(Supplier)
     * @see RecurringDateScheduled#create(Supplier)
     */
    public static <T extends ScheduledFest> T create(Supplier<T> constructor){
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

        if(ServerFest.arenaManager.requestArena(instanceType) == null) {
            Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                startUp();
            }, 20);
            return;
        }

        Fest festInstance = new Fest(instanceType);

        Bukkit.getOnlinePlayers().forEach(player -> {
            player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.65f);

            player.sendTitlePart(TitlePart.TITLE, festInstance.game().gameType().definition().iconName());
            player.sendTitlePart(TitlePart.SUBTITLE, Component.text("will start in 5 minutes", NamedTextColor.YELLOW));
        });

        //TODO: change this back to 30 second mark after testing
        final int[] timeLeft = {6}; // 30 seconds
        sendStartInMessage(timeLeft[0]);

        new BukkitRunnable() {
            @Override
            public void run() {
                timeLeft[0]--;

                if (timeLeft[0] / 60 > 0) {
                    if (timeLeft[0] % 60 == 0)
                        sendStartInMessage(timeLeft[0]);
                } else {
                    if (timeLeft[0] % 15 == 0 || timeLeft[0] == 10 || timeLeft[0] <= 5)
                        sendStartInMessage(timeLeft[0]);

                    if (timeLeft[0] <= 0) {
                        cancel();
                        festInstance.start();
                    }
                }
            }
        }.runTaskTimer(ServerFest.getPlugin(), 20L, 20L);
    }

    private void sendStartInMessage(int timeLeft) {
        int minutes = timeLeft / 60;
        int seconds = timeLeft % 60;

        Bukkit.getOnlinePlayers().forEach(player -> {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(Component.text("A ", NamedTextColor.WHITE))
                    .append(Component.text("fest ", NamedTextColor.GOLD))
                    .append(Component.text("is starting in ", NamedTextColor.WHITE))
                    .append(Component.text((minutes > 0 ? minutes : seconds), NamedTextColor.GRAY))
                    .append(Component.text((minutes > 0 ? " minutes(s)" : " second(s)"), NamedTextColor.WHITE))
            );
        });
    }

    /**
     * Writes fields of this scheduled fest into the given config section
     * @see ScheduledFest#writeExtraConfig(ConfigurationSection)
     */
    public final void writeToConfig(ConfigurationSection config){
        config.set("gameType", gameType);
        config.set("minPlayers", minPlayers);

        ConfigurationSection scheduleSection = config.getConfigurationSection("schedule");
        if(scheduleSection == null)
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
     * @see FixedDateScheduled#deserialize(ConfigurationSection) 
     * @see RecurringDateScheduled#deserialize(ConfigurationSection) 
     */
    public static ScheduledFest deserialize(ConfigurationSection config){
        String type = config.getString("schedule.type");

        UUID uuid = UUID.fromString(config.getName());
        GameType gameType = config.getObject("gameType", GameType.class, null);
        int minPlayers = config.getInt("minPlayers");

        ConfigurationSection scheduleSection = config.getConfigurationSection("schedule");

        if(scheduleSection == null)
            throw new IllegalArgumentException("Invalid schedule fest type at fest " + uuid);

        return switch (type){
            case  "fixedDate" -> FixedDateScheduled.deserialize(scheduleSection, uuid, gameType, minPlayers);
            case  "recurring" -> RecurringDateScheduled.deserialize(scheduleSection, uuid, gameType, minPlayers);
            default -> throw new IllegalArgumentException("Invalid scheduled fest type");
        };
    }

    public UUID uuid() {
        return uuid;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public ScheduledFest setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
        return this;
    }

    public @Nullable GameType gameType() {
        return gameType;
    }

    public ScheduledFest setGameType(@Nullable GameType gameType) {
        this.gameType = gameType;
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
