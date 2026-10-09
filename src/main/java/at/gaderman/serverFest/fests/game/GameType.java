package at.gaderman.serverFest.fests.game;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaDefinition;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.game.definition.*;
import at.gaderman.serverFest.fests.game.implementations.race.ElytraRace;
import at.gaderman.serverFest.fests.game.implementations.race.MountRace;
import at.gaderman.serverFest.fests.game.implementations.race.arena.ElytraRaceArenaDefinition;
import at.gaderman.serverFest.fests.game.implementations.race.arena.MountRaceArenaDefinition;
import at.gaderman.serverFest.fests.game.implementations.scored.TargetPractice;
import at.gaderman.serverFest.fests.game.implementations.tournament.PVPTournament;

public enum GameType {
    ELYTRA_RACE(new ElytraRaceDefinition()),
    MOUNT_RACE(new MountRaceDefinition()),
    TARGET_PRACTICE(new TargetPracticeDefinition()),
    PVP_TOURNAMENT(new PVPTournamentDefinition());

    private final GameDefinition definition;

    GameType(GameDefinition definition){
        this.definition = definition;
    }

    public FestGame<?> createNewInstance(Fest fest, ArenaInstance arenaInstance) {
        return switch (this) {
            case ELYTRA_RACE -> new ElytraRace(fest, arenaInstance);
            case MOUNT_RACE -> new MountRace(fest, arenaInstance);
            case TARGET_PRACTICE -> new TargetPractice(fest, arenaInstance);
            case PVP_TOURNAMENT -> new PVPTournament(fest, arenaInstance);
        };
    }

    public GameDefinition definition(){
        return definition;
    }

    public ArenaDefinition getArenaDefinition() {
        return switch (this) {
            case ELYTRA_RACE -> new ElytraRaceArenaDefinition();
            case MOUNT_RACE -> new MountRaceArenaDefinition();
            case TARGET_PRACTICE, PVP_TOURNAMENT -> new ArenaDefinition();
        };
    }
}
