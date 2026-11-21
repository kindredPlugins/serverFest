package at.gaderman.serverFest.fests.game.implementations.race.jump.implementations;

import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPattern;
import org.bukkit.Location;

import java.util.Random;

 /// Simple jump across a gap with slight vertical alteration
public class SimpleJump implements JumpPattern {

    private static final int MAX_REACH = 4;

    @Override
    public int difficulty() {
        return 1;
    }

    @Override
    public Location build(Location start, JumpContext ctx) {
        Random random = ctx.random();

        int dy = ctx.fitY(start, random.nextInt(3) - 1);
        int reach = Math.max(2, ctx.reach(MAX_REACH) - (dy > 0 ? 1 : 0));

        int dz = 2 + random.nextInt(reach - 1);
        int spare = reach - dz;
        int dx = ctx.fitX(start, random.nextInt(spare * 2 + 1) - spare);

        Location end = ctx.offset(start, dx, dy, dz);
        ctx.set(end, BASE_BLOCK);
        return end;
    }
}