package at.gaderman.serverFest.fests.game.implementations.race.jump;

import at.gaderman.serverFest.fests.game.implementations.race.jump.implementations.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//TODO: add self-registration for JumpPatterns possibly, may erase simple mistakes
/// Holds all {@link JumpPattern} implementations centrally
public final class JumpPatterns {

    private static final List<JumpPattern> ALL = List.of(
            new SimpleJump(),
            new HeadHitterJump(),
            SurfaceJump.ICE,
            SurfaceJump.SOUL_SAND,
            SurfaceJump.FENCE,
            SurfaceJump.IRON_BARS,
            LadderJump.TALL,
            LadderJump.SINGLE,
            new CornerJump(),
            new SlimeJump()
    );

    private JumpPatterns() {
    }

    /// Randomly picks a pattern which fits in the remaining budget given.
    /// Level will shift weighting to pick more difficult patterns the higher the level
    /// If a previous pattern is given it will avoid it and try to differentiate it
    public static JumpPattern pick(int budget, int level, @Nullable JumpPattern previous, Random random) {
        List<JumpPattern> pool = new ArrayList<>();
        for (JumpPattern pattern : ALL)
            if (pattern.difficulty() <= budget)
                pool.add(pattern);

        if (pool.size() > 1)
            pool.remove(previous);

        double[] weights = new double[pool.size()];
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = 1 + pool.get(i).difficulty() * (double) level;
            total += weights[i];
        }

        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++)
            if ((roll -= weights[i]) < 0)
                return pool.get(i);

        return pool.getLast();
    }
}