package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.GameType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

public abstract class ScheduledFest {

    protected int minPlayers;
    private boolean recurring = true;

    private @Nullable GameType gameType = GameType.TARGET_PRACTICE;

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

        if(ServerFest.arenaManager.requestArena(gameType) == null) {
            Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                startUp();
            }, 20);
            return;
        }

        Fest festInstance = new Fest(instanceType);

        Bukkit.getOnlinePlayers().forEach(player -> {
            player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.65f);

            player.sendTitlePart(TitlePart.TITLE, festInstance.game().iconName());
            player.sendTitlePart(TitlePart.SUBTITLE, Component.text("will start in 5 minutes", NamedTextColor.YELLOW));
        });

        //TODO: change this back to actual 5 minutes after testing
        final int[] timeLeft = {6}; // 5 minutes
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

    public int minPlayers() {
        return minPlayers;
    }

    public ScheduledFest setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
        return this;
    }

    public boolean recurring() {
        return recurring;
    }

    public ScheduledFest setRecurring(boolean recurring) {
        this.recurring = recurring;
        return this;
    }

    public @Nullable GameType gameType() {
        return gameType;
    }

    public ScheduledFest setGameType(@Nullable GameType gameType) {
        this.gameType = gameType;
        return this;
    }
}
