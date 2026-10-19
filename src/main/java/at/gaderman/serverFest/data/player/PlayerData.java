package at.gaderman.serverFest.data.player;

public class PlayerData {
    private int festsParticipated;
    private int festsWon;
    private int duelsHeld;
    private int duelsWon;
    private int championTimes;

    protected PlayerData(int festsParticipated, int festsWon, int duelsHeld, int duelsWon, int championTimes) {
        this.festsParticipated = festsParticipated;
        this.festsWon = festsWon;
        this.duelsHeld = duelsHeld;
        this.duelsWon = duelsWon;
        this.championTimes = championTimes;
    }

    public int festsParticipated() {
        return festsParticipated;
    }

    public int festsWon() {
        return festsWon;
    }

    public int duelsHeld() {
        return duelsHeld;
    }

    public int duelsWon() {
        return duelsWon;
    }

    public int championTimes() {
        return championTimes;
    }

    void setFestsParticipated(int festsParticipated) {
        this.festsParticipated = festsParticipated;
    }

    void setFestsWon(int festsWon) {
        this.festsWon = festsWon;
    }

    void setDuelsHeld(int duelsHeld) {
        this.duelsHeld = duelsHeld;
    }

    void setDuelsWon(int duelsWon) {
        this.duelsWon = duelsWon;
    }

    void setChampionTimes(int championTimes) {
        this.championTimes = championTimes;
    }
}
