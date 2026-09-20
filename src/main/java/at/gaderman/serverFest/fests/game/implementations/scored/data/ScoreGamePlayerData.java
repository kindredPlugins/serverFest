package at.gaderman.serverFest.fests.game.implementations.scored.data;

import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ScoreGamePlayerData implements FestPlayerData {
    private final UUID uuid;

    public ScoreGamePlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    private int score;
    private BossBar bossBar;

    @Override
    public void reset() {
        Player player = Bukkit.getPlayer(uuid);
        if(player == null)
            return;

        if(bossBar != null)
            bossBar.removeViewer(player);
    }

    public int score(){
        return score;
    }
    public void setScore(int score){
        this.score = score;
    }

    public BossBar bossBar() {
        return bossBar;
    }
    public BossBar setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
        return bossBar;
    }
}
