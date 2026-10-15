package at.gaderman.serverFest.fests.game.implementations.race.data;

import at.gaderman.serverFest.fests.game.interfaces.BossBarPlayerData;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;

import java.util.UUID;

public class RaceGamePlayerData implements BossBarPlayerData {
    private final UUID uuid;

    public RaceGamePlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    private int checkPoint;
    private Location previousLocation;
    private BossBar bossBar;

    @Override
    public UUID uuid() {
        return uuid;
    }

    public int currentCheckPoint(){
        return checkPoint;
    }
    public void setCheckPoint(int checkPoint){
        this.checkPoint = checkPoint;
    }

    public Location previousLocation() {
        return previousLocation;
    }

    public RaceGamePlayerData setPreviousLocation(Location previousLocation) {
        this.previousLocation = previousLocation;
        return this;
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
