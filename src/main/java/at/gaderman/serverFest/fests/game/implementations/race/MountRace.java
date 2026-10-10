package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.implementations.race.arena.RaceGameArenaLayout;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.implementations.race.track.TrackBuilder;
import at.gaderman.serverFest.fests.game.implementations.race.track.TrackPath;
import at.gaderman.serverFest.fests.game.implementations.race.track.TrackTerrain;
import at.gaderman.serverFest.fests.game.triggers.action.PlayerVehicleExitTrigger;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MountRace extends RaceGame<RaceGameArenaLayout> implements PlayerDamageTrigger, PlayerVehicleExitTrigger {

    public MountRace(Fest fest, ArenaInstance arenaInstance) {
        super(fest, arenaInstance);
    }

    @Override
    public @NotNull GameType gameType() {
        return GameType.MOUNT_RACE;
    }

    @Override
    public Location determineOrigin() {
        Location origin = super.determineOrigin();
        origin.setY(fest.world().getHighestBlockYAt(origin) + 2);
        return origin;
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        buildTrack(future);
        return future;
    }

    @Override
    protected void onStop() {
        forcedChunks.forEach(chunk -> chunk.removePluginChunkTicket(ServerFest.getPlugin()));
        forcedChunks.clear();
        fest.participants().forEach(Entity::leaveVehicle);
    }

    @Override
    protected void kickOff() {
        fest.participants().forEach(player -> {
            if (player.isInWater())
                spawnNautilusFor(player);
            else
                spawnHorseFor(player);
        });
    }

    @Override
    protected void onPassCheckPoint(Player player, int checkPoint) {
        player.sendMessage(Component.text("Passed checkpoint ", NamedTextColor.GRAY)
                .append(Component.text(checkPoint, NamedTextColor.GOLD)));
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    //region Player and Mounts

    private final Set<Chunk> forcedChunks = new HashSet<>();

    private void scheduleMountSwitcher(Player player, Vehicle vehicle) {
        boolean isHorse = vehicle instanceof Horse;
        final long[] lastSwitch = {System.currentTimeMillis()};

        vehicle.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            if (!fest.game().isRunning() || !player.isValid() || !vehicle.isValid()) {
                task.cancel();
                return;
            }

            if (System.currentTimeMillis() - lastSwitch[0] < 500)
                return;

            if (isHorse && vehicle.isInWater()) {
                switchMount(player, vehicle, true);
                lastSwitch[0] = System.currentTimeMillis();
            } else if (!isHorse && vehicle.isOnGround() && !vehicle.isInWater()) {
                switchMount(player, vehicle, false);
                lastSwitch[0] = System.currentTimeMillis();
            }
        }, null, 1, 1);
    }

    private void switchMount(Player player, Vehicle vehicle, boolean toNautilus) {
        Location location = vehicle.getLocation();
        player.spawnParticle(Particle.WHITE_SMOKE, location, 50, 0.5, 0.5, 0.5, 0.1f);
        player.playSound(location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 2f);
        vehicle.remove();

        if (toNautilus)
            spawnNautilusFor(player);
        else
            spawnHorseFor(player);
    }

    private void spawnHorseFor(Player player) {
        player.getWorld().spawn(player.getLocation(), Horse.class, horse -> {
            horse.setAdult();
            horse.setInvulnerable(true);
            horse.setTamed(true);
            horse.getEquipment().setItem(EquipmentSlot.SADDLE, ItemStack.of(Material.SADDLE));
            horse.addPassenger(player);
            horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3f);
            horse.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(1f);
            scheduleMountSwitcher(player, horse);
        });
    }

    private void spawnNautilusFor(Player player) {
        player.getWorld().spawn(player.getLocation(), Nautilus.class, nautilus -> {
            nautilus.setAdult();
            nautilus.setInvulnerable(true);
            nautilus.setTamed(true);
            nautilus.getEquipment().setItem(EquipmentSlot.SADDLE, ItemStack.of(Material.SADDLE));
            nautilus.addPassenger(player);
            scheduleMountSwitcher(player, nautilus);
        });
    }

    //endregion

    //region Track Setup

    public static final int CHECKPOINT_COUNT = 10;
    public static final int TRACK_HALF_WIDTH = TrackPath.HALF_WIDTH;
    public static final double GATE_SIZE = TRACK_HALF_WIDTH * 2 + 3;
    private static final int MIN_DEEP_WATER_DEPTH = 5;

    private void buildTrack(CompletableFuture<Void> future) {
        World world = fest.world();
        int checkpointCount = Math.min(CHECKPOINT_COUNT, checkPoints.size());

        if (checkpointCount == 0) {
            future.completeExceptionally(new IllegalStateException("MountRace has no checkpoints to build."));
            return;
        }

        Location origin = fest.origin().clone();
        List<Location> controls = createControls(origin, checkpointCount);
        List<TrackPath.Point> previewPath = TrackPath.create(controls);
        Set<ChunkPos> requiredChunks = findRequiredChunks(previewPath, controls);

        // All path and gate chunks are loaded before terrain is inspected or blocks are changed.
        loadChunks(world, requiredChunks, loadedChunks -> {
            try {
                retainChunks(loadedChunks);
                TrackTerrain terrain = new TrackTerrain(world, MIN_DEEP_WATER_DEPTH);
                List<Location> resolvedControls = new ArrayList<>(controls.size());
                resolvedControls.add(origin);

                for (int i = 0; i < checkpointCount; i++) {
                    CheckPoint checkPoint = checkPoints.get(i);
                    Location resolved = terrain.resolveCheckpoint(controls.get(i + 1));
                    checkPoint.location().set(resolved.getX(), resolved.getY(), resolved.getZ());
                    resolvedControls.add(resolved);
                }

                List<TrackPath.Point> path = TrackPath.create(resolvedControls);
                TrackBuilder.build(world, path, terrain, blockTracker::setBlock);
                createCheckpointGates(terrain, checkpointCount);
                blockTracker.save();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
    }

    private List<Location> createControls(Location origin, int checkpointCount) {
        List<Location> controls = new ArrayList<>(checkpointCount + 1);
        controls.add(origin.clone().add(0, -1, 0));

        for (int i = 0; i < checkpointCount; i++) {
            Location target = checkPoints.get(i).location().clone();
            controls.add(target.toCenterLocation());
        }

        return controls;
    }

    private Set<ChunkPos> findRequiredChunks(List<TrackPath.Point> path, List<Location> controls) {
        Set<ChunkPos> chunks = new HashSet<>();
        for (TrackPath.ChunkCoordinate coordinate : TrackPath.requiredChunks(path))
            chunks.add(new ChunkPos(coordinate.x(), coordinate.z()));
        for (Location control : controls)
            chunks.add(new ChunkPos(control.getBlockX() >> 4, control.getBlockZ() >> 4));

        int gateRadius = (int) Math.ceil(GATE_SIZE / 2) + 1;
        for (int i = 2; i < controls.size(); i++) {
            Location gate = controls.get(i);
            int x = gate.getBlockX();
            int z = gate.getBlockZ();

            for (int dx = -gateRadius; dx <= gateRadius; dx++) {
                for (int dz = -gateRadius; dz <= gateRadius; dz++)
                    chunks.add(new ChunkPos((x + dx) >> 4, (z + dz) >> 4));
            }
        }

        return chunks;
    }

    private void retainChunks(Collection<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            if (forcedChunks.add(chunk))
                chunk.addPluginChunkTicket(ServerFest.getPlugin());
        }
    }

    private void createCheckpointGates(TrackTerrain terrain, int checkpointCount) {
        for (int i = 1; i < checkpointCount; i++) {
            Location location = checkPoints.get(i).location().clone();
            TrackTerrain.ColumnInfo column = terrain.columnAt(location.getBlockX(), location.getBlockZ());

            if (!column.deepWater())
                location.add(0, GATE_SIZE / 2 * 0.8, 0);

            createGate(location, GATE_SIZE, 0.6, i == checkpointCount - 1);
        }
    }

    //endregion

    //region Track Geometry Compatibility

    public static List<TrackPoint> createPath(Location from, Location to) {
        List<TrackPath.Point> points = TrackPath.create(List.of(from, to));
        List<TrackPoint> result = new ArrayList<>(points.size());

        for (int i = 0; i < points.size(); i++) {
            TrackPath.Point point = points.get(i);
            double[] normal = TrackPath.normalAt(points, i);
            int centerX = (int) Math.floor(point.x());
            int centerZ = (int) Math.floor(point.z());
            int y = (int) Math.floor(point.y());
            Set<BlockPos> sides = new LinkedHashSet<>();

            for (int offset = -TRACK_HALF_WIDTH; offset <= TRACK_HALF_WIDTH; offset++) {
                if (offset == 0)
                    continue;

                int x = (int) Math.floor(point.x() + normal[0] * offset);
                int z = (int) Math.floor(point.z() + normal[1] * offset);
                if (x != centerX || z != centerZ)
                    sides.add(new BlockPos(x, y, z));
            }

            result.add(new TrackPoint(new BlockPos(centerX, y, centerZ), List.copyOf(sides)));
        }

        return result;
    }

    public static Set<ChunkPos> getRequiredChunks(List<TrackPoint> points) {
        Set<ChunkPos> chunks = new HashSet<>();
        for (TrackPoint point : points) {
            addChunk(chunks, point.center());
            for (BlockPos side : point.sides())
                addChunk(chunks, side);
        }
        return chunks;
    }

    private static void addChunk(Set<ChunkPos> chunks, BlockPos block) {
        chunks.add(new ChunkPos(block.x() >> 4, block.z() >> 4));
    }

    public record TrackPoint(BlockPos center, List<BlockPos> sides) {
    }

    public record BlockPos(int x, int y, int z) {
    }

    //endregion

    //region Event Handling

    @Override
    public void onPlayerDamage(EntityDamageEvent event, Player player) {
        event.setCancelled(true);
    }

    @Override
    public void onVehicleExit(VehicleExitEvent event, Player player, Vehicle vehicle) {
        if (fest.game().isRunning() && vehicle.isValid() && !player.getGameMode().isInvulnerable())
            event.setCancelled(true);
    }

    //endregion
}