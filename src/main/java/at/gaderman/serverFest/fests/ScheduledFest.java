package at.gaderman.serverFest.fests;

import java.time.LocalDateTime;

public class ScheduledFest {

    private final LocalDateTime time;

    public ScheduledFest(LocalDateTime time){
        this.time = time;
    }

    public LocalDateTime time() {
        return time;
    }
}
