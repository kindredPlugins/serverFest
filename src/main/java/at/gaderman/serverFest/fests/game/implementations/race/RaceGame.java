package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.FestGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.scheduler.BukkitRunnable;

public abstract class RaceGame extends FestGame {
    public RaceGame(Fest fest) {
        super(fest);
    }

    @Override
    public void onStart() {
        WorldBorder startBorder = Bukkit.createWorldBorder();
        startBorder.setSize(1.5);
        startBorder.setCenter(fest.world().getSpawnLocation());

        fest.participants().forEach(participant -> {
            participant.setWorldBorder(startBorder);
        });

        final int[] timeLeft = {10}; // 10 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                timeLeft[0]--;

                if(timeLeft[0] <= 5)
                    sendTitle(timeLeft[0]);

                if (timeLeft[0] <= 0) {
                    cancel();

                    fest.participants().forEach(participant -> {
                        participant.setWorldBorder(fest.world().getWorldBorder());
                        participant.playSound(participant, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                        participant.sendTitlePart(TitlePart.TITLE, Component.text("Race!", NamedTextColor.GOLD));
                    });
                    kickOff();
                }

            }
        }.runTaskTimer(ServerFest.getPlugin(), 20L, 20L);
    }

    private void sendTitle(long timeLeft) {
        fest.participants().forEach(participant -> {
            participant.playSound(participant, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            participant.sendTitlePart(TitlePart.TITLE, Component.text(timeLeft, NamedTextColor.GREEN));
        });
    }

    protected abstract void kickOff();
}
