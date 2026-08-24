package at.gaderman.serverFest.fests.schedule;

import at.gaderman.serverFest.ServerFest;
import org.bukkit.Bukkit;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class FixedDateScheduled extends ScheduledFest{

    private LocalDateTime timeStamp = LocalDateTime.now().plusSeconds(10);
    private long offsetMinutes = 10080; // 1 week

    @Override
    public void schedule() {
        LocalDateTime nextTime = nextSchedule();

        long offset = LocalDateTime.now().until(nextTime, ChronoUnit.SECONDS) * 20;
        Bukkit.getScheduler().runTaskLater(ServerFest.getPlugin(), () -> {
            startUp();
            //schedule();
        }, offset);
    }

    public LocalDateTime nextSchedule(){
        LocalDateTime current = LocalDateTime.now();

        if(timeStamp.isAfter(current))
            return timeStamp;

        while (timeStamp.isBefore(current))
            current = current.plusMinutes(offsetMinutes);

        timeStamp = current;
        return current;
    }

    public LocalDateTime timeStamp() {
        return timeStamp;
    }

    public FixedDateScheduled setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
        return this;
    }

    public long offsetMinutes() {
        return offsetMinutes;
    }

    public FixedDateScheduled setOffsetMinutes(long offsetMinutes) {
        this.offsetMinutes = offsetMinutes;
        return this;
    }
}
