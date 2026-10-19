package at.gaderman.serverFest.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.data.DataConfig;
import at.gaderman.serverFest.data.player.PlayerDataManager;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.triggers.TriggerListener;
import at.gaderman.serverFest.management.WorldManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Active instance of a fest, this means the fest is already ongoing and houses a {@link FestGame}.
 */
public class Fest {

    public static final NamespacedKey FEST_OFFSET = new NamespacedKey(ServerFest.getPlugin(), "fest_offset");
    public static final int OFFSET_INCREMENT = 10_000;

    //region Fields

    private final UUID uuid;
    private final FestType festType;
    private final List<UUID> participantUUIDs;
    private final FestGame<?> game;
    private final ArenaInstance arenaInstance;

    private World world;
    private Location origin;

    private TriggerListener triggerListener;

    public Fest(FestType festType, @NotNull GameType gameType, List<UUID> participantUUIDs) {
        this.uuid = UUID.randomUUID();
        this.festType = festType;
        this.participantUUIDs = participantUUIDs;

        this.arenaInstance = ServerFest.arenaManager.requestArena(gameType);
        this.game = gameType.createNewInstance(this, arenaInstance);

        //TODO: handle no ready arena in here but probably from outer scope
        arenaInstance.activateArena();
    }

    //endregion

    //region Properties

    public UUID uuid() {
        return uuid;
    }

    public FestType festType() {
        return festType;
    }

    public FestGame<?> game() {
        return game;
    }

    public World world() {
        return world;
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
        if (participantUUIDs.isEmpty()) {
            ServerFest.getPlugin().getLogger().info("Fest with id " + uuid + " failed due to no participants");
            return;
        }

        ServerFest.getPlugin().getLogger().info("Started fest with id " + uuid);

        this.triggerListener = new TriggerListener(this);
        FestManager.getInstance().registerFest(this);

        world = WorldManager.getInstance().loadOrCreateFestWorld();
        world.setGameRule(GameRules.SPAWN_MOBS, false);

        this.origin = adjustLocationToCenterOfChunk(game.determineOrigin());

        ScheduledTask timeOutTask = Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            List<Player> participants = participants();

            stop();

            participants.forEach(player -> {
                player.sendMessage(Component.text("The fest setup experienced a timeout, this can happen if chunks get stuck in a loading state." +
                        " Its best to wait a bit since this can happen if the server was not ready loading this are yet.", NamedTextColor.RED));
                player.playSound(player, Sound.ENTITY_WITHER_DEATH, 1, 0.5f);
            });
        }, 60 * 20);
        game.setUpWorld().thenRun(game::start).thenRun(timeOutTask::cancel)
                .whenComplete((_, throwable) -> {
                    if (throwable != null)
                        ServerFest.getPlugin().getLogger().log(Level.SEVERE, "Failed to set up or start fest " + uuid, throwable);

                    else{
                        DataConfig dataConfig = DataConfig.getInstance();

                        if(festType == FestType.SERVER_COMPETITION)
                            dataConfig.setCompetitiveRound(dataConfig.competitiveRound() + 1);

                        else if(festType == FestType.CASUAL)
                            dataConfig.setCasualFestsHeld(dataConfig.casualFestsHeld() + 1);

                        else if(festType == FestType.DUEL)
                            dataConfig.setDuelsHeld(dataConfig.duelsHeld() + 1);

                        dataConfig.setFestsHeldTotal(dataConfig.festsHeldTotal() + 1);

                        var playerDataManager = PlayerDataManager.getInstance();
                        boolean isDuel = festType == FestType.DUEL;
                        participants().forEach(participant -> {
                            if(isDuel)
                                    playerDataManager.addDuelsHeld(participant);
                            else
                                    playerDataManager.addFestsParticipated(participant);
                        });
                    }
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
        game.onLeave(player);

        participants().forEach(participant -> participant.sendMessage(
                        player.name().color(NamedTextColor.RED)
                                .append(Component.text(" has left the fest", NamedTextColor.GRAY))
                )
        );

        SaveStateManagement.restorePlayerState(player);

        if(participantUUIDs.isEmpty()){
            ServerFest.getPlugin().getLogger().info("Terminating fest " + uuid + " as all players left the fest.");
            game.stop();
        }
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
