package at.gaderman.serverFest.fests.game.implementations.race.jump.implementations;

import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPattern;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Adds in a reversed T-Shape jump (_|_) where the player has to hop around a wall
 */
public final class CornerJump implements JumpPattern {

    private static final Material MATERIAL = Material.LIGHT_BLUE_TERRACOTTA;
    private static final int BASE_REACH = 4;
    private static final int LENGTH = 2;

    @Override
    public int difficulty() {
        return 10;
    }

    @Override
    public Location build(Location start, JumpContext ctx) {
        int reach = ctx.reach(BASE_REACH);
        int dx = ctx.fitX(start, ctx.random().nextInt(3) - 1);

        Location corner = ctx.offset(start, dx, 0, reach);
        Location end = ctx.offset(corner, 0, 0, LENGTH);

        for (int i = -1; i < 4; i++) {
            ctx.set(ctx.offset(corner, 0, i, 1), MATERIAL);
        }

        ctx.set(corner, BASE_BLOCK);
        ctx.set(end, BASE_BLOCK);
        return end;
    }
}