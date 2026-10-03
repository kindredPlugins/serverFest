package at.gaderman.serverFest.fests.game.implementations.race.arena;

import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Set;

public record ElytraRaceArenaLayout(
        Location origin,
        List<CheckPoint> checkPoints,
        List<Vector> trackSamples,
        List<Integer> segmentStartIndex,
        Set<FestGame.ChunkPos> chunks
) implements RaceGameArenaLayout {
}
