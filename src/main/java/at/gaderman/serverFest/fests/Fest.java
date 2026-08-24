package at.gaderman.serverFest.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.management.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Fest {

    public static final NamespacedKey FEST_OFFSET = new NamespacedKey(ServerFest.getPlugin(), "fest_offset");
    public static final int OFFSET_INCREMENT = 10_000;

    //region Fields

    private final UUID uuid;
    private final List<UUID> participantUUIDs;
    private final FestGame game;

    private World world;
    private long offset;

    public Fest(GameType gameType) {
        this.uuid = UUID.randomUUID();
        //TODO: change this later to give players actual choice of participation
        this.participantUUIDs = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        this.game = gameType.createNewInstance(this);
    }

    //endregion

    //region Properties

    public UUID uuid() {
        return uuid;
    }

    public FestGame game() {
        return game;
    }

    public World world() {
        return world;
    }

    public long offset() {
        return offset;
    }

    public List<Player> participants() {
        return participantUUIDs.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();
    }

    //endregion

    //region Methods

    public void start() {
        ServerFest.getPlugin().getLogger().info("Started fest with id " + uuid);

//        world = WorldManager.getInstance().createChallengeWorldCreator(
//                        new NamespacedKey(ServerFest.getPlugin(), uuid.toString()))
//                .createWorld();

        world = WorldManager.getInstance().loadOrCreateFestWorld();
        world.setGameRule(GameRules.SPAWN_MOBS, false);

        PersistentDataContainer pdc = world.getPersistentDataContainer();
        offset = pdc.getOrDefault(FEST_OFFSET, PersistentDataType.LONG, 0L);
        pdc.set(FEST_OFFSET, PersistentDataType.LONG, offset + OFFSET_INCREMENT);

        game.setUpWorld();

        participants().forEach(player -> {
            player.teleport(world.getSpawnLocation());
        });

        game.onStart();
    }

    //endregion
}
