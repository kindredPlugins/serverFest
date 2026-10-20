package at.gaderman.serverFest.fests.game.implementations.scored;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.implementations.scored.data.ScoreGamePlayerData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * A type of fest game which resolves around gathering score on a time-limit
 * After the limit has run out who has the most score wins
 */
public abstract class ScoreGame<T extends ArenaLayout> extends FestGame<T> {

    private int remainingTime; //remaining time in ticks

    public ScoreGame(Fest fest, ArenaInstance arenaInstance) {
        super(fest, arenaInstance);
    }

    @Override
    public void onPlayersJoinedWorld() {
        fest.participants().forEach(participant -> {
            participant.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
        });
    }

    @Override
    protected void onCountdownComplete() {
        fest.participants().forEach(this::updateBossbar);
        kickOff();

        remainingTime = runTime();
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(ServerFest.getPlugin(), timeTask -> {
            if(!isRunning()){
                timeTask.cancel();
                return;
            }

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

            if(highestScore == 0){
                declareWinner(List.of());
                return;
            }

            List<Player> winners = fest.participants().stream().filter(player -> {
                        var playerData = (ScoreGamePlayerData) getOrCreatePlayerData(player.getUniqueId());
                        return playerData.score() >= highestScore;
                    })
                    .toList();
            declareWinner(winners);
        }, 1, 1);
    }

    protected abstract Component startTitle();

    protected abstract void kickOff();

    /**
     * The amount of time in ticks this game will run, after time is out winner is decided by most score
     */
    protected abstract int runTime();

    @Override
    protected Sound winnerCelebrationSound() {
        return Sound.MUSIC_DISC_OTHERSIDE;
    }

    @Override
    protected Component winnerAnnouncementSubtitle(@Nullable Player winner) {
        if(winner == null)
            return super.winnerAnnouncementSubtitle(null);

        var winnerData = (ScoreGamePlayerData) getOrCreatePlayerData(winner.getUniqueId());
        return super.winnerAnnouncementSubtitle(winner)
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(winnerData.score(), NamedTextColor.YELLOW))
                .append(Component.text(")", NamedTextColor.GRAY));
    }

    @Override
    protected void configureWinnerFirework(Firework firework) {
        FireworkMeta meta = firework.getFireworkMeta();
        meta.clearEffects();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.YELLOW)
                .withFade(Color.RED)
                .with(FireworkEffect.Type.STAR)
                .withFlicker()
                .withTrail()
                .build());
        firework.setFireworkMeta(meta);
        firework.setTicksToDetonate(20);
    }

    @Override
    public void onLeave(Player player) {
        super.onLeave(player);

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    @Override
    protected void onStop() {
        fest.participants().forEach(player -> player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()));
    }

    //region Visuals
    private void updateBossbar(Player participant) {
        ScoreGamePlayerData playerData = (ScoreGamePlayerData) getOrCreatePlayerData(participant.getUniqueId());

        Component title = bossBarTitle()
                .append(Component.text(" ◆ ", NamedTextColor.DARK_GRAY))
                .append(Component.text((int) Math.floor(remainingTime / 20.0), NamedTextColor.RED))
                .append(Component.text(" seconds left", NamedTextColor.GRAY));

        float progress = 1.0f * remainingTime / runTime();
        playerData.updateBossBar(participant, title, progress, BossBar.Color.RED);
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
