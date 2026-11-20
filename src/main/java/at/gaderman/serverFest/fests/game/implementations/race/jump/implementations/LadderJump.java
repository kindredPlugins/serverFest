package at.gaderman.serverFest.fests.game.implementations.race.jump.implementations;

import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPattern;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Ladder;


 /// A longer gap than a normal jump that ends on a ladder: the player grabs it and climbs onto the block on top.
 /// SINGLE is one block with one ladder (harder), TALL is two stacked blocks with two ladders (bigger target).
public final class LadderJump implements JumpPattern {

    public static final LadderJump SINGLE = new LadderJump(1, 3);
    public static final LadderJump TALL = new LadderJump(2, 2);

    /**
     * Distance from the start block to the ladder. Tune this after playtesting.
     */
    private static final int REACH = 5;

    private final int wallHeight;
    private final int difficulty;

    private LadderJump(int wallHeight, int difficulty) {
        this.wallHeight = wallHeight;
        this.difficulty = difficulty;
    }

    @Override
    public int difficulty() {
        return difficulty;
    }

    @Override
    public Location build(Location start, JumpContext ctx) {
        int dx = ctx.fitX(start, ctx.random().nextInt(3) - 1);

        Location top = ctx.offset(start, dx, 1, REACH + 1);

        for (int i = 0; i < wallHeight; i++) {
            Location wall = ctx.offset(top, 0, -i, 0);
            ctx.set(wall, BASE_BLOCK);

            Ladder ladder = (Ladder) org.bukkit.Material.LADDER.createBlockData();
            ladder.setFacing(BlockFace.NORTH); //TODO: should use unified direction
            ctx.set(ctx.offset(wall, 0, 0, -1), ladder);
        }
        return top;
    }
}