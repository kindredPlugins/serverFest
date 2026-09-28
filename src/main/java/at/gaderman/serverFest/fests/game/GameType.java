package at.gaderman.serverFest.fests.game;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaDefinition;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.arenas.implementations.ElytraRaceArenaDefinition;
import at.gaderman.serverFest.fests.arenas.implementations.ElytraRaceArenaLayout;
import at.gaderman.serverFest.fests.arenas.implementations.RaceGameArenaLayout;
import at.gaderman.serverFest.fests.game.implementations.race.ElytraRace;
import at.gaderman.serverFest.fests.game.implementations.race.MountRace;
import at.gaderman.serverFest.fests.game.implementations.scored.TargetPractice;

public enum GameType {
    ELYTRA_RACE,
    MOUNT_RACE,
    TARGET_PRACTICE;

    public FestGame<?> createNewInstance(Fest fest, ArenaInstance arenaInstance) {
        return switch (this) {
            case ELYTRA_RACE -> new ElytraRace(fest, arenaInstance);
            case MOUNT_RACE -> new MountRace(fest, arenaInstance);
            case TARGET_PRACTICE -> new TargetPractice(fest, arenaInstance);
        };
    }

    public ArenaDefinition getArenaDefinition() {
        return switch (this) {
            default -> new ElytraRaceArenaDefinition();
        };
    }
}
