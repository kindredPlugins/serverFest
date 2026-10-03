package at.gaderman.serverFest.fests.arenas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;

public class ArenaBlockTracker {

    private final Map<Block, BlockData> blockDataMap = new HashMap<>();
    private final ArenaInstance arenaInstance;

    public ArenaBlockTracker(ArenaInstance arenaInstance) {
        this.arenaInstance = arenaInstance;
    }

    public void setBlock(Location location, Material material) {
        setBlock(location.getBlock(), material);
    }

    public void setBlock(Block block, Material material) {
        blockDataMap.putIfAbsent(block, block.getBlockData());
        block.setType(material);
    }

    public void setBlock(Location location, BlockData blockData) {
        setBlock(location.getBlock(), blockData);
    }

    public void setBlock(Block block, BlockData blockData) {
        blockDataMap.putIfAbsent(block, block.getBlockData());
        block.setBlockData(blockData);
    }

    public void restore() {
        blockDataMap.forEach((block, blockData) -> {
            if (!block.getChunk().isLoaded()) {
                if (Bukkit.getServer().isStopping()) {
                    block.getWorld().getChunkAt(block.getX(), block.getZ());
                    block.setBlockData(blockData);
                    return;
                }

                block.getWorld().getChunkAtAsync(block.getLocation())
                        .thenAccept(_ -> block.setBlockData(blockData));
            } else {
                block.setBlockData(blockData);
            }
        });

        blockDataMap.clear();
    }
}
