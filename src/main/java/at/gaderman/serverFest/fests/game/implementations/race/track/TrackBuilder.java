package at.gaderman.serverFest.fests.game.implementations.race.track;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Utilizes the given information to finally build the track
 */
public final class TrackBuilder {

    private static final int HEADROOM = 25;
    private static final Material TRACK_MATERIAL = Material.SPRUCE_SLAB;

    private TrackBuilder() {
    }

    public static void build(World world, List<TrackPath.Point> path, TrackTerrain terrain, BiConsumer<Block, Material> setBlock) {
        List<TrackPath.Point> adjustedPath = terrain.adaptHeights(path);
        Map<TrackPath.ColumnPos, Double> footprint = TrackPath.footprint(adjustedPath);
        Map<TrackPath.ColumnPos, Integer> surface = resolveSurface(footprint, terrain, world);

        for (Map.Entry<TrackPath.ColumnPos, Integer> entry : surface.entrySet()) {
            TrackPath.ColumnPos column = entry.getKey();
            int y = entry.getValue();

            clearHeadroom(world, column.x(), column.z(), y, setBlock);
            setBlock.accept(world.getBlockAt(column.x(), y, column.z()), TRACK_MATERIAL);
        }
    }

    private static Map<TrackPath.ColumnPos, Integer> resolveSurface(Map<TrackPath.ColumnPos, Double> footprint, TrackTerrain terrain, World world) {
        Map<TrackPath.ColumnPos, Integer> surface = new LinkedHashMap<>(footprint.size());

        for (Map.Entry<TrackPath.ColumnPos, Double> entry : footprint.entrySet()) {
            TrackPath.ColumnPos column = entry.getKey();
            int y = (int) Math.round(entry.getValue());

            if (y <= world.getMinHeight() || y >= world.getMaxHeight())
                continue;
            if (terrain.columnAt(column.x(), column.z()).deepWaterAt(y))
                continue;

            surface.put(column, y);
        }

        return surface;
    }

    private static void clearHeadroom(World world, int x, int z, int trackY, BiConsumer<Block, Material> setBlock) {
        int highestY = Math.min(world.getHighestBlockYAt(x, z), Math.min(trackY + HEADROOM, world.getMaxHeight() - 1));
        for (int y = trackY + 1; y <= highestY; y++) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isAir())
                setBlock.accept(block, Material.AIR);
        }
    }
}
