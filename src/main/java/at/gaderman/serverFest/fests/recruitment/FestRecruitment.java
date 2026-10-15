package at.gaderman.serverFest.fests.recruitment;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestManager;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

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

    private final List<UUID> participants;

    private FestRecruitment(int remainingTime, FestType festType, GameType gameType) {
        this.remainingTime = Math.max(remainingTime, 10);
        this.festType = festType;
        this.gameType = gameType;
        this.participants = new ArrayList<>();
    }

    /**
     * Creates a new FestRecruitment instance and instantly requests it at the {@link RecruitmentManager}
     */
    public static FestRecruitment requestRecruitment(int remainingTime, FestType festType, @Nullable GameType gameType) {
        if (gameType == null)
            gameType = GameType.values()[(int) (Math.random() * GameType.values().length)];

        var recruitment = new FestRecruitment(remainingTime, festType, gameType);
        RecruitmentManager.getInstance().requestRecruitment(recruitment);
        return recruitment;
    }

    //region Scheduling

    protected void initSchedule() {
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
        new Fest(festType, gameType, participants).start();
        RecruitmentManager.getInstance().relinquishRecruitment();
    }

    //endregion

    //region Methods

    public boolean addParticipant(Player player) {
        if (FestManager.getInstance().isPlayerParticipatingInAnyFest(player))
            return false;

        participants.add(player.getUniqueId());
        sendJoinMessage(player);
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
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
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
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

            player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1, 0.65f);
            player.resetTitle();
            player.sendTitlePart(TitlePart.TITLE, gameType.definition().iconName());
            player.sendTitlePart(TitlePart.SUBTITLE, Component.text(toPlayerFriendlyName(festType.toString()), NamedTextColor.YELLOW));
        });
    }

    private void sendJoinMessage(Player joined) {
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(Component.text(joined.getName(), NamedTextColor.GREEN))
                    .append(Component.text(" joined ", NamedTextColor.GRAY))
                    .append(displayName())
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(participants.size(), NamedTextColor.GOLD))
                    .append(Component.text(")", NamedTextColor.GRAY))
            );
        });
    }

    public Component displayName() {
        return Component.text(toPlayerFriendlyName(festType.name()) + " ")
                .append((gameType == null
                        ? Component.text("Random Fest")
                        : gameType.definition().iconName()).color(TextColor.color(0xD0D0D0)))
                .color(TextColor.color(0xD0D0D0));
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

    //endregion
}
