package at.gaderman.serverFest.fests.game.interfaces;

import at.gaderman.serverFest.fests.game.FestGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;

/**
 * Interface which provides a method to make a group of players semi-transparent.
 * This is based off the general fest concept and should only be used in such.
 * Most notably used in {@link FestGame#onPlayersJoinedWorld()} to grant semi-visibility to fest participants.
 * Also adds the NEVER collision rule to simplify crowded spaces.
 */
public interface InvisiblePlayersGame {

    default void grantInvisibilityToPlayers(List<Player> players) {
        players.forEach(player -> {
            Scoreboard scoreboard = player.getScoreboard();
            if (scoreboard.equals(Bukkit.getScoreboardManager().getMainScoreboard()))
                scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

            Team team = scoreboard.getTeam("Fest");
            if (team == null)
                team = scoreboard.registerNewTeam("Fest");

            team.setCanSeeFriendlyInvisibles(true);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

            players.forEach(team::addPlayer);

            player.setScoreboard(scoreboard);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        });
    }

}
