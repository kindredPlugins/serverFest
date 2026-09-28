package at.gaderman.serverFest.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.triggers.TriggerListener;
import at.gaderman.serverFest.management.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Fest {

    public static final NamespacedKey FEST_OFFSET = new NamespacedKey(ServerFest.getPlugin(), "fest_offset");
    public static final int OFFSET_INCREMENT = 10_000;

    //region Fields

    private final UUID uuid;
    private final List<UUID> participantUUIDs;
    private final FestGame game;

    private World world;
    private Location origin;
    private long offset;

    private final TriggerListener triggerListener;

    public Fest(GameType gameType) {
        this.uuid = UUID.randomUUID();
        //TODO: change this later to give players actual choice of participation
        this.participantUUIDs = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).collect(Collectors.toList());
        this.game = gameType.createNewInstance(this, ServerFest.arenaManager.requestArenaLayout(gameType));
        this.triggerListener = new TriggerListener(this);
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

    public List<UUID> participantUUIDs() {
        return new ArrayList<>(participantUUIDs);
    }

    public List<Player> participants() {
        return participantUUIDs.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();
    }

    public Location origin() {
        return origin.clone();
    }

    //endregion

    //region Methods

    public void start() {
        ServerFest.getPlugin().getLogger().info("Started fest with id " + uuid);
        FestManager.getInstance().registerFest(this);

//        world = WorldManager.getInstance().createChallengeWorldCreator(
//                        new NamespacedKey(ServerFest.getPlugin(), uuid.toString()))
//                .createWorld();

        world = WorldManager.getInstance().loadOrCreateFestWorld();
        world.setGameRule(GameRules.SPAWN_MOBS, false);

        PersistentDataContainer pdc = world.getPersistentDataContainer();
        offset = pdc.getOrDefault(FEST_OFFSET, PersistentDataType.LONG, 0L);
        pdc.set(FEST_OFFSET, PersistentDataType.LONG, offset + OFFSET_INCREMENT);

        this.origin = adjustLocationToCenterOfChunk(game.determineOrigin());

        game.setUpWorld().thenRun(game::start).thenRun(() -> {
            origin.getChunk().removePluginChunkTicket(ServerFest.getPlugin());
            FestManager.getInstance().reserveChunkForNextFest();
        });

        CompletableFuture<?>[] teleports = participants().stream()
                .map(player -> {
                    SaveStateManagement.savePlayerState(player);
                    return player.teleportAsync(origin);
                })
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(teleports).thenRun(game::onPlayersJoinedWorld);
    }

    public void stop() {
        participants().forEach(SaveStateManagement::restorePlayerState);
        triggerListener.unregister();
        FestManager.getInstance().unregisterFest(this);
    }

    public void onLeave(Player player) {
        participantUUIDs.remove(player.getUniqueId());

        participants().forEach(participant -> participant.sendMessage(
                        player.name().color(NamedTextColor.RED)
                                .append(Component.text(" has left the fest", NamedTextColor.GRAY))
                )
        );

        SaveStateManagement.restorePlayerState(player);
    }

    private static Location adjustLocationToCenterOfChunk(Location location) {
        int centerX = (location.getChunk().getX() << 4) + 8;
        int centerZ = (location.getChunk().getZ() << 4) + 8;

        location.setX(centerX + 0.5);
        location.setZ(centerZ + 0.5);
        return location;
    }

    //endregion
}
