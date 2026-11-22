package at.gaderman.serverFest.fests.game.implementations.race.jump.implementations;

import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import org.bukkit.Location;

/// Adds a head hitter section as an extension of {@link SimpleJump}.
/// Uses this pattern as if mixed with other jumps like SurfaceJump could easily lead to illegal jumps.
/// This way it behaves like it can only follow up on a SimpleJump without affecting random spread.
public final class HeadHitterJump extends SimpleJump {

    @Override
    public int difficulty() {
        return 2;
    }

    @Override
    public Location build(Location start, JumpContext ctx) {
        Location halfPoint = super.build(start, ctx);
        Location end = ctx.offset(halfPoint, 0, 0, 2);
        ctx.set(ctx.offset(halfPoint, 0, 3, 1), BASE_BLOCK);
        ctx.set(end, BASE_BLOCK);
        return end;
    }
}