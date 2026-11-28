package at.gaderman.serverFest.fests.game.implementations.race.jump.implementations;

import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPattern;
import org.bukkit.Location;
import org.bukkit.Material;

public final class SlimeJump implements JumpPattern {

    private static final int DEPTH = 5;

    private static final int LANDING_DROP = 1;

    private static final int FALL_REACH = 4;
    private static final int BOUNCE_REACH = 3;

    @Override
    public int difficulty() {
        return 4;
    }

    @Override
    public Location build(Location start, JumpContext ctx) {
        int fall = ctx.reach(FALL_REACH);
        int bounce = ctx.reach(BOUNCE_REACH);

        Location pad = ctx.offset(start, ctx.fitX(start, ctx.random().nextInt(3) - 1), -DEPTH, fall);
        ctx.set(pad, Material.SLIME_BLOCK);

        Location landing = ctx.offset(pad, ctx.fitX(pad, ctx.random().nextInt(3) - 1), DEPTH - LANDING_DROP, bounce);
        ctx.set(landing, BASE_BLOCK);

        return ctx.offset(landing, 0, 0, 0);
    }
}