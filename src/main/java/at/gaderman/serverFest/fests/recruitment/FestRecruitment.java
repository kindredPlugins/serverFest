package at.gaderman.serverFest.fests.recruitment;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.config.GeneralConfig;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestManager;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.utils.ChatUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Served by {@link at.gaderman.serverFest.fests.schedule.ScheduledFest} at least one minute before start.
 * Schedules the starting sequence and allows players to join
 */
public class FestRecruitment {

    private int remainingTime;
    private final FestType festType;
    private final GameType gameType;
    private final int minPlayers;
    private final int maxPlayers;

    private final List<UUID> participants;
    private Fest fest;

    private final List<Player> notifiedPlayers = new ArrayList<>();

    private FestRecruitment(int remainingTime, FestType festType, GameType gameType, int minPlayers, int maxPlayers) {
        this.remainingTime = Math.max(remainingTime, 10);
        this.festType = festType;
        this.gameType = gameType;
        this.participants = new ArrayList<>();
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    /**
     * Creates a new FestRecruitment instance and instantly requests it at the {@link RecruitmentManager}
     */
    public static FestRecruitment requestRecruitment(int remainingTime, FestType festType, @Nullable GameType gameType) {
        return requestRecruitment(remainingTime, festType, gameType, 1, GeneralConfig.getInstance().maxPlayersPerFest());
    }

    /**
     * Creates a new FestRecruitment instance and instantly requests it at the {@link RecruitmentManager}
     */
    public static FestRecruitment requestRecruitment(int remainingTime, FestType festType, @Nullable GameType gameType, int minPlayers, int maxPlayers) {
        if (gameType == null)
            gameType = GameType.values()[(int) (Math.random() * GameType.values().length)];

        var recruitment = new FestRecruitment(remainingTime, festType, gameType, minPlayers, Math.clamp(maxPlayers, 1, GeneralConfig.getInstance().maxPlayersPerFest()));
        RecruitmentManager.getInstance().requestRecruitment(recruitment);
        return recruitment;
    }

    //region Scheduling

    protected void initSchedule() {
        ArenaInstance arena = ServerFest.arenaManager.requestArena(gameType, festType);
        if (arena == null) {
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendMessage(ServerFest.CHAT_PREFIX
                        .append(displayName())
                        .append(Component.text(" is being ", NamedTextColor.GRAY))
                        .append(Component.text("postponed ", NamedTextColor.RED))
                        .append(Component.text("as no arena is ready. ", NamedTextColor.GRAY))
                );
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.5f);
            });

            RecruitmentManager.getInstance().relinquishRecruitment();

            Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                RecruitmentManager.getInstance().requestRecruitment(this);
            }, 30 * 20);
            return;
        }

        fest = new Fest(festType, gameType, participants);

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            if (remainingTime <= 0) {
                startUpFest();
                task.cancel();
                return;
            }

            if (remainingTime / 60 > 0) {
                if (remainingTime % 60 == 0)
                    sendStartMessage();
            } else if (remainingTime % 15 == 0 || remainingTime == 10 || remainingTime <= 5) {
                if (remainingTime == 5)
                    sendStartTitle();

                sendStartMessage();
            }

            remainingTime--;
        }, 20, 20);
    }

    private void startUpFest() {
        // only before fest startup will offline participants be cleared, everything after counts as direct fest leave
        participants.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);

        if (participants.size() < minPlayers) {
            ServerFest.getPlugin().getLogger().info(PlainTextComponentSerializer.plainText().serialize(displayName())
                    + " failed to start as it had not enough participants (" + participants.size() + "/" + minPlayers + ")");

            Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendMessage(ServerFest.CHAT_PREFIX
                        .append(displayName())
                        .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("failed to start as it has not enough participants ", NamedTextColor.RED))
                        .append(Component.text("(", NamedTextColor.GRAY))
                        .append(Component.text(participants.size(), NamedTextColor.GOLD))
                        .append(Component.text("/", NamedTextColor.GRAY))
                        .append(Component.text(minPlayers, NamedTextColor.DARK_RED))
                        .append(Component.text(")", NamedTextColor.GRAY))
                );
                if (participants.contains(player.getUniqueId()))
                    player.playSound(player, Sound.ENTITY_WITHER_SHOOT, 1f, 0.1f);
            });

            RecruitmentManager.getInstance().relinquishRecruitment();
            fest.arenaInstance().resetToReady();
            return;
        }

        fest.start();
        RecruitmentManager.getInstance().relinquishRecruitment();
    }

    //endregion

    //region Methods

    public boolean addParticipant(Player player) {
        if (FestManager.getInstance().isPlayerParticipatingInAnyFest(player))
            return false;

        if (maxPlayers <= participants.size())
            return false;

        participants.add(player.getUniqueId());
        sendJoinMessage(player);
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);

        if(remainingTime < 5)
            sendStartTitle(player);

        return true;
    }

    public boolean removeParticipant(Player player) {
        return participants.remove(player.getUniqueId());
    }

    public boolean isParticipant(Player player) {
        return participants.contains(player.getUniqueId());
    }

    //endregion

    //region Helpers

    private void sendStartMessage() {
        int minutes = remainingTime / 60;
        int seconds = remainingTime % 60;

        Bukkit.getOnlinePlayers().forEach(player -> {
            if(!notifiedPlayers.contains(player)){
                player.playSound(player, Sound.BLOCK_BELL_USE, 1f, 1f);
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                notifiedPlayers.add(player);
            }

            if (participants.contains(player.getUniqueId()))
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            else
                player.playSound(player, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.5f, 0.3f);

            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(displayName())
                    .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("is starting in ", NamedTextColor.WHITE))
                    .append(Component.text((minutes > 0 ? minutes : seconds), NamedTextColor.GRAY))
                    .append(Component.text((minutes > 0 ? " minutes(s)" : " second(s)"), NamedTextColor.WHITE))
                    .append(participants.contains(player.getUniqueId()) ? Component.empty()
                            : Component.text(" [Join]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("fest join")))
            );
        });
    }

    private void sendStartTitle() {
        participants.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;

            sendStartTitle(player);
        });
    }

    private void sendStartTitle(Player player) {
        player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1, 0.65f);
        player.resetTitle();
        player.sendTitlePart(TitlePart.TIMES, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500)));
        player.sendTitlePart(TitlePart.TITLE, gameType.definition().iconName());
        player.sendTitlePart(TitlePart.SUBTITLE, Component.text(ChatUtils.toPlayerFriendlyName(festType.toString()), NamedTextColor.YELLOW));
    }

    private void sendJoinMessage(Player joined) {
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(Component.text(joined.getName(), NamedTextColor.GREEN))
                    .append(Component.text(" joined ", NamedTextColor.GRAY))
                    .append(displayName())
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(participants.size(), NamedTextColor.GOLD))
                    .append(Component.text("/", NamedTextColor.GRAY))
                    .append(Component.text(maxPlayers, NamedTextColor.DARK_GRAY))
                    .append(Component.text(")", NamedTextColor.GRAY))
            );
        });
    }

    public Component displayName() {
        return Component.text(festType.displayName() + " ")
                .append((gameType == null
                        ? Component.text("Random Fest")
                        : gameType.definition().iconName()).color(TextColor.color(0xD0D0D0)))
                .color(TextColor.color(0xD0D0D0));
    }

    //endregion
}
