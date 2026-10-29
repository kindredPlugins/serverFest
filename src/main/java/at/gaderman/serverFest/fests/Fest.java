package at.gaderman.serverFest.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.compatibility.GameRuleCompat;
import at.gaderman.serverFest.config.GeneralConfig;
import at.gaderman.serverFest.data.DataConfig;
import at.gaderman.serverFest.data.player.PlayerDataManager;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.arenas.ArenaState;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.triggers.TriggerListener;
import at.gaderman.serverFest.management.WorldManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
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
    private ScheduledTask maxTimeTask;

    public Fest(FestType festType, @NotNull GameType gameType, List<UUID> participantUUIDs) {
        this.uuid = UUID.randomUUID();
        this.festType = festType;
        this.participantUUIDs = participantUUIDs;

        this.arenaInstance = ServerFest.arenaManager.requestArena(gameType, festType);
        this.game = gameType.createNewInstance(this, arenaInstance);

        if (arenaInstance == null) {
            ServerFest.getPlugin().getLogger().severe("A fest has been forcefully started without checking for an empty arena slot first!");
            throw new RuntimeException("A fest has been forcefully started without checking for an empty arena slot first!");
        }

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

    public ArenaInstance arenaInstance() {
        return arenaInstance;
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
        GameRuleCompat.SPAWN_MOBS.set(world, false);

        this.origin = adjustLocationToCenterOfChunk(game.determineOrigin());

        ScheduledTask timeOutTask = Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            List<Player> participants = participants();
            game.stop();

            ServerFest.getPlugin().getLogger().severe("Fest " + uuid + " was terminated during startup due to a loading timeout.");
            participants.forEach(player -> {
                player.sendMessage(Component.text("The fest setup experienced a timeout, this can happen if chunks get stuck in a loading state." +
                        " Its best to wait a bit since this can happen if the server was not ready loading this are yet.", NamedTextColor.RED));
                player.playSound(player, Sound.ENTITY_WITHER_DEATH, 1, 0.5f);
            });
        }, 60 * 20);
        game.setUpWorld().thenRun(game::start).thenRun(timeOutTask::cancel)
                .whenComplete((_, throwable) -> {
                    if (throwable != null) {
                        ServerFest.getPlugin().getLogger().log(Level.SEVERE, "Failed to set up or start fest " + uuid, throwable);
                        game.stop();
                    } else {
                        DataConfig dataConfig = DataConfig.getInstance();

                        if (festType == FestType.SERVER_COMPETITION)
                            dataConfig.setCompetitiveRound(dataConfig.competitiveRound() + 1);

                        else if (festType == FestType.CASUAL)
                            dataConfig.setCasualFestsHeld(dataConfig.casualFestsHeld() + 1);

                        else if (festType == FestType.DUEL)
                            dataConfig.setDuelsHeld(dataConfig.duelsHeld() + 1);

                        dataConfig.setFestsHeldTotal(dataConfig.festsHeldTotal() + 1);

                        var playerDataManager = PlayerDataManager.getInstance();
                        boolean isDuel = festType == FestType.DUEL;
                        participants().forEach(participant -> {
                            if (isDuel)
                                playerDataManager.addDuelsHeld(participant);
                            else
                                playerDataManager.addFestsParticipated(participant);
                        });

                        scheduleMaxTimeTask();
                    }
                });

        CompletableFuture<?>[] teleports = participants().stream()
                .map(player -> {
                    SaveStateManagement.savePlayerState(player);

                    player.eject();
                    player.leaveVehicle();
                    if (player.isSleeping())
                        player.wakeup(false);

                    return player.teleportAsync(origin)
                            .thenAccept(teleported -> {
                                if (!teleported) {
                                    player.sendMessage(Component.text("You could not be teleported", NamedTextColor.RED));
                                    onLeave(player);
                                }
                            });
                })
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(teleports).thenRun(game::onPlayersJoinedWorld);
    }

    public void stop() {
        if (maxTimeTask != null)
            maxTimeTask.cancel();

        if (arenaInstance.arenaState() == ArenaState.ACTIVE)
            arenaInstance.resetToReady();

        participants().forEach(SaveStateManagement::restorePlayerState);

        if (triggerListener != null)
            triggerListener.unregister();
        FestManager.getInstance().unregisterFest(this);
    }

    public void onLeave(Player player) {
        participantUUIDs.remove(player.getUniqueId());
        game.onLeave(player);

        player.sendMessage(ServerFest.CHAT_PREFIX
                .append(Component.text("You left the fest", NamedTextColor.RED))
        );
        participants().forEach(participant -> participant.sendMessage(
                        player.name().color(NamedTextColor.RED)
                                .append(Component.text(" has left the fest", NamedTextColor.GRAY))
                )
        );

        SaveStateManagement.restorePlayerState(player);

        if (participantUUIDs.isEmpty()) {
            ServerFest.getPlugin().getLogger().info("Terminating fest " + uuid + " as all players left the fest.");
            game.stop();
        }
    }

    /**
     * Called when an ender pearl of a player is triggered outside the fest world. This will intercept the event
     * and then overwrite the players location value with the pearl result.
     */
    public void onEnderPearlTeleportAway(PlayerTeleportEvent event, Player player) {
        event.setCancelled(true);

        SaveStateManagement.updateSaveLocation(player, event.getTo());
        player.playSound(player, Sound.ENTITY_PLAYER_TELEPORT, 1f, 1f);
        player.sendMessage(ServerFest.CHAT_PREFIX
                .append(Component.text("Your backup location has been updated to your recent ", NamedTextColor.GRAY))
                .append(Component.text("ender pearl hit", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(".", NamedTextColor.GRAY))
        );
    }

    private void scheduleMaxTimeTask() {
        int warnTime = 30;

        maxTimeTask = Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            participants().forEach(participant -> {
                participant.playSound(participant, Sound.ENTITY_WITHER_AMBIENT, 1f, 0.3f);
                participant.sendMessage(Component.text("This fest will terminate in 30 seconds due to lasting too long", NamedTextColor.RED));
            });

            maxTimeTask = Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                maxTimeTask = null;

                //fest is already celebrating winner, should not be terminated now, over soon anyway
                if (!game.isRunning())
                    return;

                ServerFest.getPlugin().getLogger().info("Fest " + uuid + " was forcefully stopped as it lasted longer than 5 minutes");
                List<Player> participants = participants();
                game.stop();

                participants.forEach(participant -> {
                    participant.playSound(participant, Sound.ENTITY_WITHER_DEATH, 1f, 0.3f);
                    participant.sendMessage(Component.text("Terminated this fest as it took over 5 minutes to complete.", NamedTextColor.RED));
                });
            }, warnTime * 20);
        }, (GeneralConfig.getInstance().maxFestDuration() - warnTime) * 20L);
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
