package at.gaderman.serverFest.fests.game.implementations.scored.data;

import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;

public class ScoreGamePlayerData implements FestPlayerData {

    private int score;

    @Override
    public void reset() {

    }

    public int score(){
        return score;
    }

    public void setScore(int score){
        this.score = score;
    }
}
