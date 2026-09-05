package at.gaderman.serverFest.fests.game.implementations.race.data;

import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import org.bukkit.Location;

public class RaceGamePlayerData implements FestPlayerData {
    private int checkPoint;
    private Location previousLocation;

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
}
