package at.gaderman.serverFest.fests.game;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.implementations.race.ElytraRace;
import at.gaderman.serverFest.fests.game.implementations.race.MountRace;
import at.gaderman.serverFest.fests.game.implementations.scored.TargetPractice;

public enum GameType {
    ELYTRA_RACE,
    MOUNT_RACE,
    TARGET_PRACTICE;

    public FestGame createNewInstance(Fest fest){
        return switch (this){
            case ELYTRA_RACE -> new ElytraRace(fest);
            case MOUNT_RACE -> new MountRace(fest);
            case TARGET_PRACTICE ->  new TargetPractice(fest);
        };
    }
}
