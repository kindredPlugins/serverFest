package at.gaderman.serverFest.fests.recruitment;

import at.gaderman.serverFest.ServerFest;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.SynchronousQueue;

/**
 * Manages {@link FestRecruitment}, ensuring only one recruitment can be active at a time, all recruitments have to be
 * requested from here.
 */
public class RecruitmentManager {

    private static RecruitmentManager instance;

    public static RecruitmentManager getInstance(){
        if(instance == null) instance = new RecruitmentManager();
        return instance;
    }

    private @Nullable FestRecruitment activeRecruitment;
    private final Queue<FestRecruitment> recruitmentQueue = new SynchronousQueue<>();

    private RecruitmentManager(){

    }

    protected void requestRecruitment(FestRecruitment recruitment){
        if(activeRecruitment == null){
            activeRecruitment = recruitment;
            activeRecruitment.initSchedule();
            return;
        }

        recruitmentQueue.add(recruitment);
        ServerFest.getPlugin().getLogger().info("Fest " + recruitment.displayName() + " has been enlisted in the recruitment queue");
    }

    protected void relinquishRecruitment(){
        activeRecruitment = null;

        if(recruitmentQueue.isEmpty())
            return;

        activeRecruitment = recruitmentQueue.poll();
        activeRecruitment.initSchedule();
    }

    public @Nullable FestRecruitment activeRecruitment(){
        return activeRecruitment;
    }

    public boolean addPlayerToActiveRecruitment(Player player){
        if(activeRecruitment == null)
            return false;

        return activeRecruitment.addParticipant(player);
    }
}
