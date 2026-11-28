package at.gaderman.serverFest.fests.game.implementations.race.jump;

import org.bukkit.Location;
import org.bukkit.Material;

/// specific pattern of how blocks are aligned, e.g. single jump with a gap or slime jump, ladder jump, etc.
public interface JumpPattern {
    Material BASE_BLOCK = Material.BLUE_TERRACOTTA;

    /// difficulty cost of the jump, evaluated in each checkpoint section to balance difficulty
    int difficulty();

    /**
     * @param start the block the player stands on. Never modified.
     * @return the block the player stands on once the pattern is done (always a new Location)
     */
    Location build(Location start, JumpContext ctx);
}
