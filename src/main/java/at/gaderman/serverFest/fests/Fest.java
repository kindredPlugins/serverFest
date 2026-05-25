package at.gaderman.serverFest.fests;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Fest {

    private final UUID uuid;
    private final List<UUID> participantUUIDs;

    public Fest(UUID uuid, List<UUID> participantUUIDs) {
        this.uuid = uuid;
        this.participantUUIDs = participantUUIDs;
    }

    public UUID uuid() {
        return uuid;
    }

    public List<Player> participants() {
        return participantUUIDs.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();
    }
}
