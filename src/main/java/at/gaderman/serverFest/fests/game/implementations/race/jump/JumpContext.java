package at.gaderman.serverFest.fests.game.implementations.race.jump;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Random;
import java.util.function.BiConsumer;

/// Helper for "context" when building and selecting jumps for {@link at.gaderman.serverFest.fests.game.implementations.race.JumpAndRun}
public final class JumpContext {

    private final Random random;
    private final BiConsumer<Location, BlockData> sink;
    private final int minX, maxX, minY, maxY;

    public JumpContext(Random random, BiConsumer<Location, BlockData> sink,
                       int minX, int maxX, int minY, int maxY) {
        this.random = random;
        this.sink = sink;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    public Random random() {
        return random;
    }

    /** A new Location relative to base. Always use this instead of base.add(...), which mutates base. */
    public Location offset(Location base, int dx, int dy, int dz) {
        return base.clone().add(dx, dy, dz);
    }

    public void set(Location pos, Material material) {
        set(pos, material.createBlockData());
    }

    public void set(Location pos, BlockData data) {
        sink.accept(pos.clone(), data); // cloned so later mutation of pos can never move a recorded block
    }

    /** Mirrors dx when it would leave the lane. */
    public int fitX(Location from, int dx) {
        int target = from.getBlockX() + dx;
        return target < minX || target > maxX ? -dx : dx;
    }

    /** Mirrors dy when it would leave the allowed height range. */
    public int fitY(Location from, int dy) {
        int target = from.getBlockY() + dy;
        return target < minY || target > maxY ? -dy : dy;
    }
}