package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.implementations.race.data.RaceGamePlayerData;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class RaceGame extends FestGame {

    protected List<CheckPoint> checkPoints = new ArrayList<>();

    public RaceGame(Fest fest) {
        super(fest);
    }

    @Override
    protected void onStart() {
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

                if (timeLeft[0] <= 5)
                    sendTitle(timeLeft[0]);

                if (timeLeft[0] <= 0) {
                    cancel();

                    fest.participants().forEach(participant -> {
                        participant.setWorldBorder(fest.world().getWorldBorder());
                        participant.playSound(participant, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                        participant.sendTitlePart(TitlePart.TITLE, Component.text("Race!", NamedTextColor.GOLD));
                    });
                    activateCheckPointDetection();
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

    @Override
    protected void onWinner(List<Player> winner) {
        Player singleWinner = winner.getFirst();

        fest.participants().forEach(participant -> {
            boolean isWinner = winner.contains(participant);

            if (isWinner) {
                participant.sendTitlePart(TitlePart.TITLE, Component.text("Winner!", NamedTextColor.GOLD));
                participant.getWorld().playSound(participant, Sound.MUSIC_DISC_PIGSTEP, 1f, 1.3f);

                participant.getWorld().spawn(participant.getLocation(), Firework.class, firework -> {
                    FireworkMeta meta = firework.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(Color.GREEN)
                            .with(FireworkEffect.Type.CREEPER)
                            .withFlicker()
                            .withTrail()
                            .withFade(Color.LIME)
                            .build());

                    firework.setFireworkMeta(meta);
                });
            } else {
                participant.sendTitlePart(TitlePart.TITLE, winner.getFirst().displayName().color(NamedTextColor.AQUA)
                        .append(Component.text(" has won!", NamedTextColor.GREEN)));
                participant.playSound(participant, Sound.ENTITY_WITHER_DEATH, 1f, 1.3f);
            }

            participant.sendTitlePart(TitlePart.SUBTITLE, MiniMessage.miniMessage().deserialize("<head:" + singleWinner.getUniqueId() + "> ")
                    .append(singleWinner.name().color(NamedTextColor.GOLD))
                    .append(Component.text(" has won!", NamedTextColor.GREEN)));
        });

        Bukkit.getServer().getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            stop();
        }, 200);
    }

    protected abstract void onPassCheckPoint(Player player, int checkPoint);

    private void activateCheckPointDetection() {
        fest.participants().forEach(participant -> {
            participant.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
                if (!fest.game().isRunning()) {
                    task.cancel();
                    return;
                }

                RaceGamePlayerData playerData = (RaceGamePlayerData) getOrCreatePlayerData(participant.getUniqueId());
                int currentCheckPoint = playerData.currentCheckPoint();

                if (currentCheckPoint + 1 >= checkPoints.size()) {
                    declareWinner(List.of(participant));
                    return;
                }

                CheckPoint nextCheckPoint = checkPoints.get(currentCheckPoint + 1);
                Location current = participant.getLocation();
                Location previous = playerData.previousLocation();

                if (previous != null && crossedCheckpoint(previous, current, nextCheckPoint)) {
                    playerData.setCheckPoint(currentCheckPoint + 1);

                    onPassCheckPoint(participant, currentCheckPoint + 1);
                }

                playerData.setPreviousLocation(current);
            }, null, 1, 1);
        });
    }

    private boolean crossedCheckpoint(Location previous, Location current, CheckPoint checkpoint) {
        Location center = checkpoint.location();

        Vector prev = toLocal(previous, center);
        Vector curr = toLocal(current, center);

        if ((prev.getZ() < 0 && curr.getZ() < 0) || (prev.getZ() > 0 && curr.getZ() > 0))
            return false;

        double dz = curr.getZ() - prev.getZ();

        if (Math.abs(dz) < 1e-9)
            return false;

        double t = -prev.getZ() / dz;

        if (t < 0.0 || t > 1.0)
            return false;

        double crossingX = prev.getX() + (curr.getX() - prev.getX()) * t;
        double crossingY = prev.getY() + (curr.getY() - prev.getY()) * t;

        double halfWidth = checkpoint.width() / 2.0;
        double halfHeight = checkpoint.height() / 2.0;

        return Math.abs(crossingX) <= halfWidth && Math.abs(crossingY) <= halfHeight;
    }

    private Vector toLocal(Location position, Location checkpoint) {
        double dx = position.getX() - checkpoint.getX();
        double dy = position.getY() - checkpoint.getY();
        double dz = position.getZ() - checkpoint.getZ();

        double radians = Math.toRadians(checkpoint.getYaw());

        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        double localX = dx * cos + dz * sin;
        double localZ = -dx * sin + dz * cos;

        return new Vector(localX, dy, localZ);
    }

    @Override
    protected @NonNull FestPlayerData createPlayerData(UUID uuid) {
        return new RaceGamePlayerData();
    }
}
