package at.gaderman.serverFest.fests.arenas;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Responsible for generating an {@link ArenaLayout} suitable for a specific fest game, this focuses mainly on the required chunks
 * in order for the {@link ArenaManager} to pre-generate terrain but also generate certain infrastructure needed for fest games in one go.
 */
public interface ArenaDefinition {
    @NotNull ArenaLayout generateLayout(Location origin);
}
