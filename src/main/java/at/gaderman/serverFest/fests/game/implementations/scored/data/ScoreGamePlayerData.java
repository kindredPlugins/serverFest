package at.gaderman.serverFest.fests.game.implementations.scored.data;

import at.gaderman.serverFest.fests.game.interfaces.BossBarPlayerData;
import net.kyori.adventure.bossbar.BossBar;

import java.util.UUID;

public class ScoreGamePlayerData implements BossBarPlayerData {
    private final UUID uuid;

    public ScoreGamePlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    private int score;
    private BossBar bossBar;

    @Override
    public UUID uuid() {
        return uuid;
    }

    public int score(){
        return score;
    }
    public void setScore(int score){
        this.score = score;
    }

    @Override
    public BossBar bossBar() {
        return bossBar;
    }

    @Override
    public BossBar setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
        return bossBar;
    }
}
