package at.gaderman.serverFest.fests.game.implementations.race.track;

import at.gaderman.serverFest.fests.game.implementations.race.MountRace;
import org.bukkit.*;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles terrain interpretation like adjusting height in water or on surfaces
 */
public final class TrackTerrain {

    private static final double MAX_GRADE = 0.35;
    private final World world;
    private final int minDeepWaterDepth;
    private final Map<Long, ColumnInfo> columns = new HashMap<>();

    public TrackTerrain(World world, int minDeepWaterDepth) {
        this.world = world;
        this.minDeepWaterDepth = minDeepWaterDepth;
    }

    //region Terrain Queries

    public ColumnInfo columnAt(int x, int z) {
        return columns.computeIfAbsent(columnKey(x, z), ignored -> readColumn(x, z));
    }

    public Location resolveCheckpoint(Location target) {
        Location location = target.clone().toCenterLocation();
        ColumnInfo column = columnAt(location.getBlockX(), location.getBlockZ());

        if (column.deepWater())
            location.setY(column.water().randomInteriorY());
        else if (column.water() != null)
            location.setY(column.water().surfaceY() + 1);
        else
            location.setY(column.groundY() + 1);

        return location;
    }

    private ColumnInfo readColumn(int x, int z) {
        int minY = world.getMinHeight();
        int highestY = Math.min(world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE), world.getMaxHeight() - 1);
        int groundY = minY;
        int waterSurfaceY = Integer.MIN_VALUE;
        int waterDepth = 0;
        boolean countingWater = false;

        for (int y = highestY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();

            if (waterSurfaceY == Integer.MIN_VALUE && type == Material.WATER) {
                waterSurfaceY = y;
                waterDepth = 1;
                countingWater = true;
            } else if (countingWater) {
                if (isWaterLike(type))
                    waterDepth++;
                else
                    countingWater = false;
            }

            if (isGroundBlock(block)) {
                groundY = y;
                if (waterSurfaceY == Integer.MIN_VALUE || !countingWater)
                    break;
            }
        }

        WaterColumn water = waterSurfaceY == Integer.MIN_VALUE ? null : new WaterColumn(waterSurfaceY, waterDepth);
        return new ColumnInfo(groundY, water, minDeepWaterDepth);
    }

    private boolean isGroundBlock(Block block) {
        Material type = block.getType();
        return !block.isPassable() && !block.isLiquid() && !isWaterPlant(type)
                && !Tag.LEAVES.isTagged(type) && !Tag.LOGS.isTagged(type);
    }

    private boolean isWaterLike(Material type) {
        return type == Material.WATER || isWaterPlant(type);
    }

    private boolean isWaterPlant(Material type) {
        return switch (type) {
            case KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS -> true;
            default -> false;
        };
    }

    private long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    //endregion

    //region Height Profile

    public List<TrackPath.Point> adaptHeights(List<TrackPath.Point> path) {
        if (path.isEmpty())
            return List.of();

        int size = path.size();
        double[] heights = new double[size];

        for (int i = 0; i < size; i++) {
            TrackPath.Point point = path.get(i);
            double height = point.y();

            if (!point.isCheckpoint()) {
                ColumnInfo column = columnAt((int) Math.floor(point.x()), (int) Math.floor(point.z()));
                if (!column.deepWaterAt((int) Math.floor(height))) {
                    double groundSurface = column.groundY() + 1;
                    if (Math.abs(height - groundSurface) <= 2)
                        height = groundSurface;
                }
            }

            heights[i] = height;
        }

        double[] smoothed = heights.clone();
        for (int i = 1; i < size - 1; i++) {
            if (path.get(i).isCheckpoint())
                continue;

            double total = 0;
            double weightTotal = 0;
            for (int offset = -2; offset <= 2; offset++) {
                int neighbor = i + offset;
                if (neighbor < 0 || neighbor >= size)
                    continue;

                double weight = 3 - Math.abs(offset);
                total += heights[neighbor] * weight;
                weightTotal += weight;
            }
            smoothed[i] = total / weightTotal;
        }

        limitGrade(path, smoothed);

        List<TrackPath.Point> adjusted = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            TrackPath.Point point = path.get(i);
            adjusted.add(new TrackPath.Point(point.x(), smoothed[i], point.z(), point.checkpointIndex()));
        }
        return adjusted;
    }

    private void limitGrade(List<TrackPath.Point> path, double[] heights) {
        for (int i = 1; i < heights.length; i++) {
            if (path.get(i).isCheckpoint())
                continue;

            double maxChange = horizontalDistance(path.get(i - 1), path.get(i)) * MAX_GRADE;
            heights[i] = clamp(heights[i], heights[i - 1] - maxChange, heights[i - 1] + maxChange);
        }

        for (int i = heights.length - 2; i >= 0; i--) {
            if (path.get(i).isCheckpoint())
                continue;

            double maxChange = horizontalDistance(path.get(i), path.get(i + 1)) * MAX_GRADE;
            heights[i] = clamp(heights[i], heights[i + 1] - maxChange, heights[i + 1] + maxChange);
        }
    }

    private double horizontalDistance(TrackPath.Point from, TrackPath.Point to) {
        return Math.hypot(to.x() - from.x(), to.z() - from.z());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    //endregion

    public record ColumnInfo(int groundY, WaterColumn water, int minDeepWaterDepth) {
        public boolean deepWater() {
            return water != null && water.depth() >= minDeepWaterDepth;
        }

        public boolean deepWaterAt(int y) {
            return deepWater() && water.contains(y);
        }
    }

    public record WaterColumn(int surfaceY, int depth) {
        public boolean contains(int y) {
            return y <= surfaceY && y > surfaceY - depth;
        }

        public int randomInteriorY() {
            int minY = surfaceY - depth + 1 + (int) (MountRace.GATE_SIZE / 2);
            int maxY = surfaceY - 1;
            if (minY > maxY)
                return surfaceY;
            return ThreadLocalRandom.current().nextInt(minY, maxY + 1);
        }
    }
}
