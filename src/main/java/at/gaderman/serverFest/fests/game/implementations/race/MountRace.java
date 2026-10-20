package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.implementations.race.arena.MountRaceArenaDefinition;
import at.gaderman.serverFest.fests.game.implementations.race.arena.RaceGameArenaLayout;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.implementations.race.data.RaceGamePlayerData;
import at.gaderman.serverFest.fests.game.triggers.action.PlayerVehicleExitTrigger;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
        int surfaceY = getTrackSurfaceY(fest.world(), origin.getBlockX(), origin.getBlockZ());
        origin.setY(surfaceY + 0.5);
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
    protected @NonNull CompletableFuture<Void> onReset() {
        return super.onReset().thenRun(() -> mounts.forEach(Vehicle::remove));
    }

    @Override
    protected void kickOff() {
        fest.participants().forEach(participant -> {
            spawnHorseFor(participant);

            WorldBorder border = Bukkit.createWorldBorder();
            border.setCenter(participant.getLocation());
            border.setSize(MountRaceArenaDefinition.POSSIBLE_WIDTH * 8);
            participant.setWorldBorder(border);

            double originZ = fest.origin().getZ();
            double finalZ = checkPoints.getLast().location().getZ();
            participant.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
                if (!fest.game().isRunning()) {
                    task.cancel();
                    return;
                }

                RaceGamePlayerData playerData = (RaceGamePlayerData) getOrCreatePlayerData(participant.getUniqueId());
                Location checkPoint = checkPoints.get(playerData.currentCheckPoint()).location();
                border.setCenter(checkPoint.x(), Math.clamp(participant.getZ(), originZ, finalZ));
                participant.setWorldBorder(border);
            }, null, 4, 4);
        });
    }

    @Override
    protected void onPassCheckPoint(Player player, int checkPoint) {
        player.sendMessage(Component.text("Passed checkpoint ", NamedTextColor.GRAY)
                .append(Component.text(checkPoint, NamedTextColor.GOLD)));
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    //region Player

    private final Set<Chunk> forcedChunks = new HashSet<>();
    private final Set<Vehicle> mounts = new HashSet<>();

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

        mounts.remove(vehicle);
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
            horse.setPersistent(false);
            mounts.add(horse);

            //TODO: readd mount switcher later on, but v1.0 cannot handle such a complex path, just horse will be used for now

            // scheduleMountSwitcher(player, horse);
        });
    }

    private void spawnNautilusFor(Player player) {
        player.getWorld().spawn(player.getLocation(), Nautilus.class, nautilus -> {
            nautilus.setAdult();
            nautilus.setInvulnerable(true);
            nautilus.setTamed(true);
            nautilus.getEquipment().setItem(EquipmentSlot.SADDLE, ItemStack.of(Material.SADDLE));
            nautilus.addPassenger(player);
            nautilus.setPersistent(false);
            scheduleMountSwitcher(player, nautilus);
            mounts.add(nautilus);
        });
    }

    //endregion

    //region Track

    public static final int CHECKPOINT_COUNT = 10;
    public static final int TRACK_DIAMETER = 2;
    public static final int MAX_TRACK_HEIGHT_CHANGE = 3;
    public static final double GATE_SIZE = TRACK_DIAMETER * 2 + 3;

    private void buildTrack(CompletableFuture<Void> future) {
        World world = fest.world();
        Location origin = fest.origin().clone();
        origin.setY(getTrackSurfaceY(world, origin.getBlockX(), origin.getBlockZ()) + 0.5);
        buildTrackSection(future, world, origin, TRACK_DIAMETER, GATE_SIZE, CHECKPOINT_COUNT, 0);
    }

    private static final double RAMP_SLOPE = 0.5;
    private static final int MAX_STEP_Y = 3;
    private static final int MAX_CUT_DEPTH = 3;
    private static final int REPOSITION_STEP = 6;
    private static final int REPOSITION_MAX = 24;

    private record TrackPlan(Location target, List<TrackPoint> path, int[] levels, int maxCut) {}

    private void buildTrackSection(CompletableFuture<Void> future, World world, Location lastLocation,
                                   int trackDiameter, double gateSize, int checkpointCount, int index) {
        if (index >= checkpointCount) {
            future.complete(null);
            return;
        }

        CheckPoint checkPoint = checkPoints.get(index);

        List<Location> targets = createCandidateTargets(world, lastLocation,
                checkPoint.location().getX(), checkPoint.location().getZ());

        Set<ChunkPos> required = new HashSet<>();
        for (Location target : targets)
            required.addAll(getRequiredChunks(createPath(lastLocation, target)));

        loadChunks(world, required, resolvedChunks -> {
            int startLevel = lastLocation.getBlockY();

            TrackPlan plan = null;
            for (Location target : targets) {
                TrackPlan candidate = planTrack(world, lastLocation, target, startLevel);
                if (plan == null || candidate.maxCut() < plan.maxCut())
                    plan = candidate;
                if (plan.maxCut() <= MAX_CUT_DEPTH)
                    break;
            }

            Set<ChunkPos> chosenChunks = getRequiredChunks(plan.path());
            resolvedChunks.forEach(chunk -> {
                if (chosenChunks.contains(new ChunkPos(chunk.getX(), chunk.getZ())) && forcedChunks.add(chunk))
                    chunk.addPluginChunkTicket(ServerFest.getPlugin());
            });

            List<TrackPoint> path = plan.path();
            int[] levels = plan.levels();

            for (int i = 0; i < path.size(); i++) {
                TrackPoint point = path.get(i);
                int level = levels[i];

                List<BlockPos> blocks = new ArrayList<>();
                blocks.add(point.center());
                blocks.addAll(point.sides());

                for (BlockPos pos : blocks) {
                    blockTracker.setBlock(world.getBlockAt(pos.x(), level, pos.z()), Material.SPRUCE_SLAB);

                    for (int clearY = level + 1; clearY < level + 10; clearY++) {
                        Block clearance = world.getBlockAt(pos.x(), clearY, pos.z());
                        if (!clearance.isLiquid())
                            blockTracker.setBlock(clearance, Material.AIR);
                    }
                }
            }

            int endLevel = levels.length > 0 ? levels[levels.length - 1] : startLevel;
            Location newLocation = plan.target();
            newLocation.setY(endLevel);
            checkPoint.location().set(newLocation.getX(), endLevel, newLocation.getZ());

            blockTracker.save();

            if (index != 0)
                createGate(checkPoint.location().clone().add(0, gateSize * 0.4, 0),
                        gateSize, 0.6, index == checkpointCount - 1);

            buildTrackSection(future, world, newLocation, trackDiameter, gateSize, checkpointCount, index + 1);
        });
    }

    private TrackPlan planTrack(World world, Location from, Location target, int startLevel) {
        List<TrackPoint> path = createPath(from, target);
        int n = path.size();
        if (n == 0)
            return new TrackPlan(target, path, new int[0], 0);

        int[] terrain = new int[n];
        for (int i = 0; i < n; i++) {
            BlockPos c = path.get(i).center();
            terrain[i] = getTrackSurfaceY(world, c.x(), c.z());
        }

        double[] envelope = new double[n];
        for (int i = 0; i < n; i++) envelope[i] = terrain[i];
        for (int i = 1; i < n; i++)
            envelope[i] = Math.max(envelope[i], envelope[i - 1] - RAMP_SLOPE);
        for (int i = n - 2; i >= 0; i--)
            envelope[i] = Math.max(envelope[i], envelope[i + 1] - RAMP_SLOPE);

        int[] levels = new int[n];
        double walked = startLevel;
        levels[0] = startLevel;
        int maxCut = Math.max(0, terrain[0] - startLevel);

        for (int i = 1; i < n; i++) {
            walked = Math.clamp(envelope[i], walked - RAMP_SLOPE, walked + RAMP_SLOPE);
            int level = Math.clamp((int) Math.round(walked), levels[i - 1] - MAX_STEP_Y, levels[i - 1] + MAX_STEP_Y);
            levels[i] = level;

            maxCut = Math.max(maxCut, terrain[i] - level);
        }

        return new TrackPlan(target, path, levels, maxCut);
    }

    private List<Location> createCandidateTargets(World world, Location from, double x, double z) {
        List<Location> targets = new ArrayList<>();
        targets.add(new Location(world, x, from.getY(), z));

        double dx = x - from.getX();
        double dz = z - from.getZ();
        double length = Math.hypot(dx, dz);
        if (length == 0)
            return targets;

        double perpX = -dz / length;
        double perpZ = dx / length;

        for (int offset = REPOSITION_STEP; offset <= REPOSITION_MAX; offset += REPOSITION_STEP) {
            targets.add(new Location(world, x + perpX * offset, from.getY(), z + perpZ * offset));
            targets.add(new Location(world, x - perpX * offset, from.getY(), z - perpZ * offset));
        }
        return targets;
    }

    private int getTrackSurfaceY(World world, int x, int z) {
        int waterSurfaceY = Integer.MIN_VALUE;

        for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();

            if (type == Material.SPRUCE_SLAB)
                continue;

            if (block.isLiquid()) {
                if (waterSurfaceY == Integer.MIN_VALUE)
                    waterSurfaceY = y;
                continue;
            }

            if (isTreePart(type) || block.isPassable())
                continue;

            return waterSurfaceY == Integer.MIN_VALUE ? y + 1 : waterSurfaceY + 1;
        }

        if (waterSurfaceY != Integer.MIN_VALUE)
            return waterSurfaceY + 1;

        return world.getMinHeight() + 1;
    }

    private boolean isLeaves(Material material) {
        return material.name().endsWith("_LEAVES");
    }

    private boolean isTreePart(Material material) {
        String name = material.name();

        return isLeaves(material)
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || name.endsWith("_ROOTS")
                || name.equals("MUSHROOM_STEM")
                || name.equals("BAMBOO_BLOCK")
                || name.equals("BAMBOO");
    }

    public static List<TrackPoint> createPath(Location from, Location to) {
        List<TrackPoint> points = new ArrayList<>();

        Vector start = from.toVector();
        Vector end = to.toVector();
        Vector line = end.clone().subtract(start);

        Vector lineXZ = line.clone().setY(0);
        double length = lineXZ.length();

        if (length == 0)
            return points;

        int steps = Math.max(1, (int) Math.ceil(length));

        for (int i = 0; i <= steps; i++) {
            double t = Math.min(1, i / (double) steps);

            double x = from.getX() + (to.getX() - from.getX()) * t;
            double z = from.getZ() + (to.getZ() - from.getZ()) * t;
            int y = Location.locToBlock(from.getY() + (to.getY() - from.getY()) * t);

            int centerX = Location.locToBlock(x);
            int centerZ = Location.locToBlock(z);

            BlockPos center = new BlockPos(centerX, y, centerZ);
            List<BlockPos> sides = resolveSideBlocks(centerX, y, centerZ);

            points.add(new TrackPoint(center, sides));
        }

        return points;
    }

    private static @NonNull List<BlockPos> resolveSideBlocks(int centerX, int y, int centerZ) {
        List<BlockPos> sides = new ArrayList<>();

        for (int offsetX = -TRACK_DIAMETER; offsetX <= TRACK_DIAMETER; offsetX++) {
            for (int offsetZ = -TRACK_DIAMETER; offsetZ <= TRACK_DIAMETER; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0)
                    continue;

                double distance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
                if (distance > TRACK_DIAMETER)
                    continue;

                sides.add(new BlockPos(centerX + offsetX, y, centerZ + offsetZ));
            }
        }
        return sides;
    }

    public static Set<ChunkPos> getRequiredChunks(List<TrackPoint> points) {
        return points.stream()
                .flatMap(point -> {
                    List<BlockPos> blocks = new ArrayList<>();
                    blocks.add(point.center());
                    blocks.addAll(point.sides());
                    return blocks.stream();
                })
                .map(block -> new ChunkPos(block.x() >> 4, block.z() >> 4))
                .collect(Collectors.toSet());
    }

    //endregion

    //region Event Handling

    @Override
    public void onPlayerDamage(EntityDamageEvent event, Player player) {
        event.setCancelled(true);
    }

    @SuppressWarnings("removal")
    @Override
    public void onVehicleExit(VehicleExitEvent event, Player player, Vehicle vehicle) {
        if (fest.game().isRunning() && vehicle.isValid() && !player.getGameMode().isInvulnerable())
            event.setCancelled(true);

        if(!isRunning())
            return;

        RaceGamePlayerData playerData = (RaceGamePlayerData) getOrCreatePlayerData(player.getUniqueId());
        int checkPoint = playerData.currentCheckPoint();
        if (checkPoint == 0)
            vehicle.teleport(fest.origin(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        else
            vehicle.teleport(checkPoints.get(checkPoint).location().toCenterLocation(), TeleportFlag.EntityState.RETAIN_PASSENGERS);

        player.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
        player.sendActionBar(Component.text("Returned to checkpoint", NamedTextColor.YELLOW));
    }

    //endregion

    public record TrackPoint(BlockPos center, List<BlockPos> sides) {
    }

    public record BlockPos(int x, int y, int z) {
    }
}