package at.gaderman.serverFest.fests.game.implementations.race.jump.implementations;

import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPattern;
import org.bukkit.Location;

/**
 * Forward, a sideways hop around the corner, forward again. Easy hops, the difficulty is the direction change.
 */
public final class CornerJump implements JumpPattern {

    private static final int HOP = 3;

    @Override
    public int difficulty() {
        return 3;
    }

    @Override
    public Location build(Location start, JumpContext ctx) {
        Location corner = ctx.offset(start, 0, 0, HOP);

        int side = ctx.fitX(corner, ctx.random().nextBoolean() ? HOP : -HOP);
        Location turn = ctx.offset(corner, side, 0, 0);
        Location end = ctx.offset(turn, 0, 0, HOP);

        ctx.set(corner, BASE_BLOCK);
        ctx.set(turn, BASE_BLOCK);
        ctx.set(end, BASE_BLOCK);
        return end;
    }
}