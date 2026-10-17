package at.gaderman.serverFest.fests;

//TODO: implement in fests, currently unused
/**
 * How a fest is interpreted for the server and its consequences, decided already at {@link at.gaderman.serverFest.fests.schedule.ScheduledFest} level
 */
public enum FestType {

    /**
     * Fest held just for fun, the plugin will not do any extra work besides organizing the fest, players decide what
     * to do with the result of it.
     */
    CASUAL,

    /**
     * Usually a recurring date fest, which is about earning the glory of the whole server, winners probably earn a reward.
     * However, they are at least marked as Fest Winner, a unique status
     */
    SERVER_COMPETITION,

    /**
     * Internal fest type which cannot be selected for scheduling, this happens when 2 players face off in a duel.
     * May contain a bet which the winner earns. Lower-priority than other fests.
     */
    DUEL;

    /**
     * Is a valid option for a scheduled fest?
     */
    public boolean isSelectectibleForScheduling()
    {
        return this != DUEL;
    }

    public String displayName(){
        return switch (this){
            case CASUAL -> "Casual";
            case SERVER_COMPETITION -> "Competitive";
            case DUEL -> "Duel";
        };
    }
}
