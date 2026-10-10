package at.gaderman.serverFest.fests.game.implementations.race.track;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the exact points along two checkPoints/controls for creating the actual track blocks and uses tangents
 * with interpolation to smooth out turns
 */
public final class TrackPath {

    public static final int HALF_WIDTH = 2;
    private static final double SAMPLE_SPACING = 0.5;
    private static final double CURVE_SAMPLE_FACTOR = 1.35;

    private TrackPath() {
    }

    //region Centerline

    public static List<Point> create(List<Location> controls) {
        if (controls.size() < 2)
            return List.of();

        double[][] tangents = calculateTangents(controls);
        List<Point> points = new ArrayList<>();

        for (int segment = 0; segment < controls.size() - 1; segment++) {
            Location from = controls.get(segment);
            Location to = controls.get(segment + 1);
            double distance = horizontalDistance(from.getX(), from.getZ(), to.getX(), to.getZ());
            int steps = Math.max(1, (int) Math.ceil(distance * CURVE_SAMPLE_FACTOR / SAMPLE_SPACING));

            for (int step = 0; step < steps; step++) {
                double t = step / (double) steps;
                double x = hermite(from.getX(), to.getX(), tangents[segment][0], tangents[segment + 1][0], t);
                double z = hermite(from.getZ(), to.getZ(), tangents[segment][1], tangents[segment + 1][1], t);
                double y = from.getY() + (to.getY() - from.getY()) * t;
                points.add(new Point(x, y, z, step == 0 ? segment : -1));
            }
        }

        Location last = controls.get(controls.size() - 1);
        points.add(new Point(last.getX(), last.getY(), last.getZ(), controls.size() - 1));
        return points;
    }

    private static double[][] calculateTangents(List<Location> controls) {
        double[][] tangents = new double[controls.size()][2];

        for (int i = 0; i < controls.size(); i++) {
            Location current = controls.get(i);
            double dx;
            double dz;

            if (i == 0) {
                Location next = controls.get(i + 1);
                dx = next.getX() - current.getX();
                dz = next.getZ() - current.getZ();
            } else if (i == controls.size() - 1) {
                Location previous = controls.get(i - 1);
                dx = current.getX() - previous.getX();
                dz = current.getZ() - previous.getZ();
            } else {
                Location previous = controls.get(i - 1);
                Location next = controls.get(i + 1);
                dx = (next.getX() - previous.getX()) * 0.5;
                dz = (next.getZ() - previous.getZ()) * 0.5;

                double incoming = horizontalDistance(previous.getX(), previous.getZ(), current.getX(), current.getZ());
                double outgoing = horizontalDistance(current.getX(), current.getZ(), next.getX(), next.getZ());
                double maxLength = Math.min(incoming, outgoing) * 0.9;
                double length = Math.hypot(dx, dz);

                if (length > maxLength && length > 0) {
                    double scale = maxLength / length;
                    dx *= scale;
                    dz *= scale;
                }
            }

            tangents[i][0] = dx;
            tangents[i][1] = dz;
        }

        return tangents;
    }

    private static double hermite(double from, double to, double fromTangent, double toTangent, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double h00 = 2 * t3 - 3 * t2 + 1;
        double h10 = t3 - 2 * t2 + t;
        double h01 = -2 * t3 + 3 * t2;
        double h11 = t3 - t2;
        return h00 * from + h10 * fromTangent + h01 * to + h11 * toTangent;
    }

    //endregion

    //region Road Footprint

    public static Map<ColumnPos, Double> footprint(List<Point> points) {
        Map<ColumnPos, HeightAccumulator> heights = new LinkedHashMap<>();
        forEachFootprintCell(points, (x, z, y) -> addHeight(heights, x, z, y));

        Map<ColumnPos, Double> result = new LinkedHashMap<>(heights.size());
        heights.forEach((pos, accumulator) -> result.put(pos, accumulator.average()));
        return result;
    }

    public static Set<ChunkCoordinate> requiredChunks(List<Point> points) {
        Set<ChunkCoordinate> chunks = new LinkedHashSet<>();
        forEachFootprintCell(points, (x, z, y) -> chunks.add(new ChunkCoordinate(x >> 4, z >> 4)));
        return chunks;
    }

    private static void forEachFootprintCell(List<Point> points, FootprintConsumer consumer) {
        for (int i = 0; i < points.size() - 1; i++) {
            Point from = points.get(i);
            Point to = points.get(i + 1);
            double dx = to.x() - from.x();
            double dz = to.z() - from.z();
            double lengthSquared = dx * dx + dz * dz;

            if (lengthSquared < 1.0E-8)
                continue;

            double length = Math.sqrt(lengthSquared);
            double normalX = -dz / length;
            double normalZ = dx / length;
            double reach = HALF_WIDTH + 0.5 * (Math.abs(normalX) + Math.abs(normalZ));
            double alongMargin = 0.5 * (Math.abs(dx / length) + Math.abs(dz / length)) / length;
            int minX = (int) Math.floor(Math.min(from.x(), to.x()) - reach - 0.5);
            int maxX = (int) Math.floor(Math.max(from.x(), to.x()) + reach - 0.5);
            int minZ = (int) Math.floor(Math.min(from.z(), to.z()) - reach - 0.5);
            int maxZ = (int) Math.floor(Math.max(from.z(), to.z()) + reach - 0.5);

            for (int x = minX; x <= maxX; x++) {
                double centerX = x + 0.5;
                for (int z = minZ; z <= maxZ; z++) {
                    double centerZ = z + 0.5;
                    double along = ((centerX - from.x()) * dx + (centerZ - from.z()) * dz) / lengthSquared;
                    if (along < -alongMargin || along > 1 + alongMargin)
                        continue;

                    double perpendicular = Math.abs((centerX - from.x()) * dz - (centerZ - from.z()) * dx) / length;
                    if (perpendicular > reach)
                        continue;

                    double t = Math.max(0, Math.min(1, along));
                    consumer.accept(x, z, from.y() + (to.y() - from.y()) * t);
                }
            }
        }
    }

    public static double[] normalAt(List<Point> points, int index) {
        Point current = points.get(index);
        Point previous = points.get(Math.max(0, index - 1));
        Point next = points.get(Math.min(points.size() - 1, index + 1));

        double dx = next.x() - previous.x();
        double dz = next.z() - previous.z();

        if (Math.hypot(dx, dz) < 1.0E-8) {
            if (index + 1 < points.size()) {
                dx = next.x() - current.x();
                dz = next.z() - current.z();
            } else if (index > 0) {
                dx = current.x() - previous.x();
                dz = current.z() - previous.z();
            }
        }

        double length = Math.hypot(dx, dz);
        if (length < 1.0E-8)
            return new double[]{0, 1};

        return new double[]{-dz / length, dx / length};
    }

    private static void addHeight(Map<ColumnPos, HeightAccumulator> heights, int x, int z, double y) {
        heights.computeIfAbsent(new ColumnPos(x, z), ignored -> new HeightAccumulator()).add(y);
    }

    private static double horizontalDistance(double x1, double z1, double x2, double z2) {
        return Math.hypot(x2 - x1, z2 - z1);
    }

    //endregion

    public record Point(double x, double y, double z, int checkpointIndex) {
        public boolean isCheckpoint() {
            return checkpointIndex >= 0;
        }
    }

    public record ColumnPos(int x, int z) {
    }

    public record ChunkCoordinate(int x, int z) {
    }

    @FunctionalInterface
    private interface FootprintConsumer {
        void accept(int x, int z, double y);
    }

    private static final class HeightAccumulator {
        private double total;
        private int count;

        private void add(double y) {
            total += y;
            count++;
        }

        private double average() {
            return total / count;
        }
    }
}
