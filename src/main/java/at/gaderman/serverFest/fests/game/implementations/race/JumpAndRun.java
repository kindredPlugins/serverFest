package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.implementations.race.arena.RaceGameArenaLayout;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.implementations.race.data.RaceGamePlayerData;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class JumpAndRun extends RaceGame<RaceGameArenaLayout> implements PlayerDamageTrigger {
    public JumpAndRun(Fest fest, ArenaInstance arenaInstance) {
        super(fest, arenaInstance);
    }

    private static final int CHECKPOINT_COUNT = 8;
    private static final int AVERAGE_JUMPS_PER_CHECKPOINT = 8;
    private static final int CHECKPOINT_VARIANCE = 2;
    private static final int MAX_JUMP_DISTANCE = 4;

    private static final int FALL_RESET_DISTANCE = 10;

    private final List<JumpPosition> jumpPositions = new ArrayList<>();

    @Override
    public @NotNull GameType gameType() {
        return GameType.JUMP_AND_RUN;
    }

    @Override
    public Location determineOrigin() {
        Location origin = super.determineOrigin();
        origin.setY(origin.getWorld().getMaxHeight() - AVERAGE_JUMPS_PER_CHECKPOINT * CHECKPOINT_COUNT * 2);
        return origin;
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        //TODO: start platform and proper spacing of jumps, more than just regular jumps

        Location cursor = fest.origin().clone();
        World world = cursor.getWorld();

        for (int i = 0; i <= CHECKPOINT_COUNT; i++) {
            jumpPositions.add(new JumpPosition(cursor.clone(), Material.EMERALD_BLOCK));
            jumpPositions.add(new JumpPosition(cursor.clone().add(0, 1, 0), Material.HEAVY_WEIGHTED_PRESSURE_PLATE));
            checkPoints.add(new CheckPoint(cursor.clone().add(0, 1, 0), 1, 1));

            if (i == CHECKPOINT_COUNT) continue;

            int jumpsToPlace = AVERAGE_JUMPS_PER_CHECKPOINT
                    + ThreadLocalRandom.current().nextInt((CHECKPOINT_VARIANCE + 1) * 2) - CHECKPOINT_VARIANCE;

            for (int j = 0; j < jumpsToPlace; j++) {
                int xDiff = 1 + ThreadLocalRandom.current().nextInt(MAX_JUMP_DISTANCE);
                int zDiff = 1 + ThreadLocalRandom.current().nextInt(MAX_JUMP_DISTANCE - xDiff + 1);
                int yDiff = xDiff + zDiff < MAX_JUMP_DISTANCE && ThreadLocalRandom.current().nextBoolean() ? 1 : 0;

                cursor.add(xDiff, yDiff, zDiff);
                jumpPositions.add(new JumpPosition(cursor.clone(), Material.BLUE_TERRACOTTA));
            }
        }

        Set<ChunkPos> chunks = new HashSet<>();
        jumpPositions.forEach(jumpPos -> {
            int chunkX = jumpPos.location.getBlockX() >> 4;
            int chunkZ = jumpPos.location.getBlockZ() >> 4;
            chunks.add(new ChunkPos(chunkX, chunkZ));
        });


        CompletableFuture<Void> future = new CompletableFuture<>();
        loadChunks(world, chunks, _ -> {

            updateArenaBuildingBar(Component.text("Placing blocks"), 0.9f);
            jumpPositions.forEach(pos -> blockTracker.setBlock(pos.location, pos.material));

            blockTracker.save();
            updateArenaBuildingBar(Component.text("Waiting for server"), 1.0f);

            future.complete(null);
        });

        return future;
    }

    @Override
    protected void kickOff() {
        fest.participants().forEach(participant -> {
            participant.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
                if(!isRunning()){
                    task.cancel();
                    return;
                }

                Location location = participant.getLocation();
                RaceGamePlayerData playerData = (RaceGamePlayerData) getOrCreatePlayerData(participant.getUniqueId());
                CheckPoint currentCheckPoint = checkPoints.get(playerData.currentCheckPoint());

                if(location.getY() > currentCheckPoint.location().y() - FALL_RESET_DISTANCE) return;

                participant.teleportAsync(currentCheckPoint.location());
                participant.playHurtAnimation(0.0f);
                participant.playSound(participant, Sound.ENTITY_GENERIC_HURT, 1f, 1f);
            }, null, 1, 1);
        });
    }

    @Override
    protected void onPassCheckPoint(Player player, int checkPoint) {
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.85f);
    }

    @Override
    protected void onStop() {

    }

    //region Methods



    //endregion

    //region Helper Classes

    private record JumpPosition(Location location, Material material) {
    }

    //endregion

    @Override
    public void onPlayerDamage(EntityDamageEvent event, Player player) {
        event.setCancelled(true);
    }
}
