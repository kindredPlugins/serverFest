package at.gaderman.serverFest.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.recruitment.RecruitmentManager;
import at.gaderman.serverFest.management.WorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static at.gaderman.serverFest.fests.Fest.FEST_OFFSET;

public class FestManager {
    private static FestManager instance;

    public static FestManager getInstance() {
        if (instance == null) instance = new FestManager();
        return instance;
    }

    private final Map<UUID, Fest> fests = new LinkedHashMap<>();

    private final World festWorld;

    private FestManager() {
        festWorld = WorldManager.getInstance().loadOrCreateFestWorld();

        //reserveChunkForNextFest();
    }

    public void registerFest(Fest fest) {
        this.fests.put(fest.uuid(), fest);
    }

    public void unregisterFest(Fest fest) {
        this.fests.remove(fest.uuid());
    }

    public @Nullable Fest getFest(UUID uuid) {
        return this.fests.getOrDefault(uuid, null);
    }

    public List<Fest> getFests() {
        return new ArrayList<>(this.fests.values());
    }

    public boolean isPlayerParticipatingInAnyFest(Player player) {
        return RecruitmentManager.getInstance().isPlayerParticipant(player) ||
                this.fests.values().stream().anyMatch(fest -> fest.participantUUIDs().contains(player.getUniqueId()));
    }

    /**
     * Schedules a plugin chunk ticket for the chunk the next fest will start from
     */
    public void reserveChunkForNextFest() {
        PersistentDataContainer pdc = festWorld.getPersistentDataContainer();
        long offset = pdc.getOrDefault(FEST_OFFSET, PersistentDataType.LONG, 0L);

        new Location(festWorld, offset, festWorld.getSeaLevel(), 0).getChunk().addPluginChunkTicket(ServerFest.getPlugin());
        ServerFest.getPlugin().getLogger().info("Main chunk for the next fest has been reserved");
    }
}
