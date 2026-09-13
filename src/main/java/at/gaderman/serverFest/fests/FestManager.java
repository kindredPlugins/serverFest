package at.gaderman.serverFest.fests;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FestManager {
    private static FestManager instance;

    private  FestManager() {
    }

    public static FestManager getInstance() {
        if (instance == null) instance = new FestManager();
        return instance;
    }

    private final Map<UUID, Fest> fests = new LinkedHashMap<>();

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
}
