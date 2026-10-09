package at.gaderman.serverFest.fests.game.implementations.tournament;

import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;

import java.util.UUID;

public class TournamentData implements FestPlayerData {

    private final UUID uuid;
    private PlayerTournamentState state;

    public TournamentData(UUID uuid){
        this.uuid = uuid;

        this.state = PlayerTournamentState.WAITING;
    }

    @Override
    public void reset() {

    }

    public PlayerTournamentState state() {
        return state;
    }

    public TournamentData setState(PlayerTournamentState state) {
        this.state = state;
        return this;
    }
}
