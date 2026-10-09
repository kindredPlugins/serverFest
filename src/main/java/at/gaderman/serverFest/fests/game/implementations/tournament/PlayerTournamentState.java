package at.gaderman.serverFest.fests.game.implementations.tournament;

public enum PlayerTournamentState {
    /**
     * Player is waiting for their next match, similar to spectator in what they can actually do but is still
     * a possible winner
     */
    WAITING,

    /**
     * During a faceoff inside the tournament, the actual gameplay is performed in this state
     */
    BATTLE,

    /**
     * Player is already eliminated and just spectates until a winner is decided
     */
    SPECTATOR;
}
