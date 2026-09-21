package at.gaderman.serverFest.fests.game.implementations.scored;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.implementations.scored.data.ScoreGamePlayerData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Comparator;
import java.util.List;

/**
 * A type of fest game which resolves around gathering score on a time-limit
 * After the limit has run out who has the most score wins
 */
public abstract class ScoreGame extends FestGame {

    private int remainingTime; //remaining time in ticks

    public ScoreGame(Fest fest) {
        super(fest);
    }

    @Override
    public void onPlayersJoinedWorld() {
        fest.participants().forEach(participant -> {
            participant.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
        });
    }

    @Override
    protected void onStart() {
        final int[] timeLeft = {10}; // 10 seconds

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            timeLeft[0]--;

            if (timeLeft[0] <= 5)
                sendTitle(timeLeft[0]);

            if (timeLeft[0] <= 0) {
                task.cancel();

                fest.participants().forEach(participant -> {
                    participant.setWorldBorder(fest.world().getWorldBorder());
                    participant.playSound(participant, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                    participant.sendTitlePart(TitlePart.TITLE, startTitle());

                    updateBossbar(participant);
                });
                kickOff();

                remainingTime = runTime();

                Bukkit.getGlobalRegionScheduler().runAtFixedRate(ServerFest.getPlugin(), timeTask -> {
                    remainingTime--;

                    List<Player> players = fest.participants();
                    players.forEach(this::updateBossbar);

                    float secondsLeft = remainingTime / 20.0f;
                    if (secondsLeft == 10 || secondsLeft == 5 || (secondsLeft <= 3 && secondsLeft == Math.round(secondsLeft)))
                        players.forEach(player -> player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1));

                    if (remainingTime > 0)
                        return;

                    timeTask.cancel();

                    players.forEach(player -> player.getInventory().clear());

                    int highestScore = players.stream().map(player -> {
                        var playerData = (ScoreGamePlayerData) getOrCreatePlayerData(player.getUniqueId());
                        return playerData.score();
                    }).reduce(Integer::max).orElse(0);

                    List<Player> winners = fest.participants().stream().filter(player -> {
                                var playerData = (ScoreGamePlayerData) getOrCreatePlayerData(player.getUniqueId());
                                return playerData.score() >= highestScore;
                            })
                            .toList();
                    declareWinner(winners);
                }, 1, 1);
            }
        }, 20, 20);
    }

    private void sendTitle(long timeLeft) {
        fest.participants().forEach(participant -> {
            participant.playSound(participant, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            participant.sendTitlePart(TitlePart.TITLE, Component.text(timeLeft, NamedTextColor.GREEN));
        });
    }

    protected abstract Component startTitle();

    protected abstract void kickOff();

    /**
     * The amount of time in ticks this game will run, after time is out winner is decided by most score
     */
    protected abstract int runTime();

    //TODO: proper winning not just copy paste, its actually possible for a draw, needs to be considered or addressed
    @Override
    protected void onWinner(List<Player> winner) {
        Player singleWinner = winner.getFirst();

        var winnerData = (ScoreGamePlayerData) getOrCreatePlayerData(singleWinner.getUniqueId());

        fest.participants().forEach(participant -> {
            boolean isWinner = winner.contains(participant);
            participant.resetTitle();

            if (isWinner) {
                participant.sendTitlePart(TitlePart.TITLE, Component.text("Winner!", NamedTextColor.GOLD));
                participant.getWorld().playSound(participant, Sound.MUSIC_DISC_OTHERSIDE, 1f, 1.3f);

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
                    firework.setTicksToDetonate(40);
                });
            } else {
                participant.sendTitlePart(TitlePart.TITLE, winner.getFirst().displayName().color(NamedTextColor.AQUA)
                        .append(Component.text(" has won!", NamedTextColor.GREEN)));
                participant.playSound(participant, Sound.ENTITY_WITHER_DEATH, 1f, 1.3f);
            }

            participant.sendTitlePart(TitlePart.SUBTITLE, MiniMessage.miniMessage().deserialize("<head:" + singleWinner.getUniqueId() + "> ")
                    .append(singleWinner.name().color(NamedTextColor.GOLD))
                    .append(Component.text(" has won!", NamedTextColor.GREEN))
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(winnerData.score(), NamedTextColor.YELLOW))
                    .append(Component.text(")", NamedTextColor.GRAY)));
        });

        Bukkit.getServer().getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            stop();
        }, 200);
    }

    //region Visuals

    private void updateBossbar(Player participant) {
        ScoreGamePlayerData playerData = (ScoreGamePlayerData) getOrCreatePlayerData(participant.getUniqueId());

        if (playerData.bossBar() == null) {
            BossBar bossBar = BossBar.bossBar(bossBarTitle(), 0.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            bossBar.addViewer(participant);
            playerData.setBossBar(bossBar);
        }

        BossBar bossBar = playerData.bossBar();
        bossBar.name(bossBarTitle()
                .append(Component.text(" ◆ ", NamedTextColor.DARK_GRAY))
                .append(Component.text((int) Math.floor(remainingTime / 20.0), NamedTextColor.RED))
                .append(Component.text(" seconds left", NamedTextColor.GRAY)
                ));

        float progress = 1.0f * remainingTime / runTime();
        bossBar.progress(Math.clamp(progress, 0, 1));
    }

    protected abstract Component bossBarTitle();

    protected void updateScoreboard() {
        List<Scoring> allScores = fest.participants().stream().map(player -> {
            var playerData = (ScoreGamePlayerData) getOrCreatePlayerData(player.getUniqueId());
            return new Scoring(player, playerData.score());
        }).toList();

        List<Scoring> topScores = allScores.stream()
                .sorted(Comparator.comparing(scoring -> scoring.score))
                .limit(8)
                .toList();

        fest.participants().forEach(player -> {
            Scoreboard board = player.getScoreboard();

            if (board.equals(Bukkit.getScoreboardManager().getMainScoreboard()))
                board = Bukkit.getScoreboardManager().getNewScoreboard();

            Objective scoreObj = board.getObjective("score");
            if (scoreObj != null)
                scoreObj.unregister();

            scoreObj = board.registerNewObjective("score", Criteria.DUMMY, Component.text("Score", NamedTextColor.GOLD));
            scoreObj.setDisplaySlot(DisplaySlot.SIDEBAR);

            Objective finalScoreObj = scoreObj;
            topScores.forEach(scoring -> finalScoreObj.getScore(scoring.player).setScore(scoring.score));

            if (topScores.stream().noneMatch(scoring -> scoring.player.equals(player))) {
                var playerData = (ScoreGamePlayerData) getOrCreatePlayerData(player.getUniqueId());
                scoreObj.getScore(player).setScore(playerData.score());

                scoreObj.getScore("...").setScore(playerData.score());
            }

            player.setScoreboard(board);
        });
    }

    //endregion

    private record Scoring(Player player, int score) {
    }
}
