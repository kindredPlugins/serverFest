package at.gaderman.serverFest.fests.game.implementations.race.data;

import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public class RaceGamePlayerData implements FestPlayerData {
    private final UUID uuid;

    public RaceGamePlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    private int checkPoint;
    private Location previousLocation;
    private BossBar bossBar;

    @Override
    public void reset() {
        Player player = Bukkit.getPlayer(uuid);
        if(player == null)
            return;

        if(bossBar != null)
            bossBar.removeViewer(player);
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

    public BossBar bossBar() {
        return bossBar;
    }
    public BossBar setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
        return bossBar;
    }
}
