package at.gaderman.serverFest.fests.game.implementations.race.jump.implementations;

import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPattern;
import org.bukkit.Location;
import org.bukkit.Material;


/// Similar to a SimpleJump but uses a different material to influence the jump
public final class SurfaceJump implements JumpPattern {

    public static final SurfaceJump ICE = new SurfaceJump(Material.PACKED_ICE, 0);
    public static final SurfaceJump SOUL_SAND = new SurfaceJump(Material.SOUL_SAND, 0);
    public static final SurfaceJump IRON_BARS = new SurfaceJump(Material.IRON_BARS, 0);

    public static final SurfaceJump FENCE = new SurfaceJump(Material.OAK_FENCE, -1);

    private final Material material;
    private final int drop;

    private SurfaceJump(Material material, int drop) {
        this.material = material;
        this.drop = drop;
    }

    @Override
    public int difficulty() {
        return 2;
    }

    @Override
    public Location build(Location start, JumpContext ctx) {
        int dz = 2 + ctx.random().nextInt(2);                                  // 2..3
        int dx = ctx.fitX(start, ctx.random().nextInt(3) - 1);                 // -1..1
        int dy = ctx.fitY(start, drop);

        Location end = ctx.offset(start, dx, dy, dz);
        ctx.set(end, material);
        return end;
    }
}