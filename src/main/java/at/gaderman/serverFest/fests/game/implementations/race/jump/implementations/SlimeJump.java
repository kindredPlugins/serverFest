package at.gaderman.serverFest.fests.game.implementations.race.jump.implementations;

import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPattern;
import org.bukkit.Location;
import org.bukkit.Material;

 /// Jump onto a slime block, get bounced, and land on a second platform. Essentially "two" blocks placed at once.
public final class SlimeJump implements JumpPattern {

    @Override
    public int difficulty() {
        return 4;
    }

    @Override
    public Location build(Location start, JumpContext ctx) {
        int slimeDx = ctx.fitX(start, ctx.random().nextInt(3) - 1);
        Location slime = ctx.offset(start, slimeDx, 0, 3);

        int sideways = ctx.fitX(slime, ctx.random().nextBoolean() ? 1 : -1);
        Location landing = ctx.offset(slime, sideways, 0, 3);

        ctx.set(slime, Material.SLIME_BLOCK);
        ctx.set(landing, BASE_BLOCK);
        return landing;
    }
}