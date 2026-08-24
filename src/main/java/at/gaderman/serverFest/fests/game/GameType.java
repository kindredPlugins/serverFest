package at.gaderman.serverFest.fests.game;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.implementations.race.ElytraRace;
import at.gaderman.serverFest.fests.game.implementations.race.MountRace;

public enum GameType {
    ELYTRA_RACE,
    MOUNT_RACE;

    public FestGame createNewInstance(Fest fest){
        return switch (this){
            case ELYTRA_RACE -> new ElytraRace(fest);
            case MOUNT_RACE -> new MountRace(fest);
        };
    }
}
