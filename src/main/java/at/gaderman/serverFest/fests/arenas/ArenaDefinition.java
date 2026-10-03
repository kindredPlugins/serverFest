package at.gaderman.serverFest.fests.arenas;

import at.gaderman.serverFest.fests.game.FestGame;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Responsible for generating an {@link ArenaLayout} suitable for a specific fest game, this focuses mainly on the required chunks
 * in order for the {@link ArenaManager} to pre-generate terrain but also generate certain infrastructure needed for fest games in one go.
 * The default implementation serves surrounding chunks in a 4-chunk radius around the origin, for general loading
 */
public class ArenaDefinition {

    public @NotNull ArenaLayout generateLayout(Location origin) {
        Set<FestGame.ChunkPos> chunks = new HashSet<>();

        Chunk chunk = origin.getChunk();

        int radius = 4;
        for (int x = 0; x < radius; x++) {
            for (int z = 0; z < radius; z++) {
                chunks.add(new FestGame.ChunkPos(chunk.getX() + x - radius / 2, chunk.getZ() + z - radius / 2));
            }
        }

        return new ArenaLayout() {
            @Override
            public Location origin() {
                return origin;
            }

            @Override
            public Set<FestGame.ChunkPos> chunks() {
                return chunks;
            }
        };
    }
}
