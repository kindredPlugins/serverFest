package at.gaderman.serverFest.fests.arenas.implementations.race;

import at.gaderman.serverFest.fests.arenas.ArenaDefinition;
import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.implementations.race.MountRace;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MountRaceArenaDefinition implements ArenaDefinition {

    @Override
    public @NotNull ArenaLayout generateLayout(Location origin) {
        World world = origin.getWorld();
        List<CheckPoint> checkPoints = new ArrayList<>();
        Set<FestGame.ChunkPos> chunks = new LinkedHashSet<>();

        Location lastLocation = origin.clone();
        for (int i = 0; i < MountRace.CHECKPOINT_COUNT; i++) {
            double newX = lastLocation.getX() + (Math.random() - 0.5) * 45;
            double newZ = lastLocation.getZ() + Math.random() * 15 + 30;

            Location newLocation = new Location(world, newX, lastLocation.getY(), newZ);
            checkPoints.add(new CheckPoint(newLocation.toCenterLocation(), MountRace.GATE_SIZE, MountRace.GATE_SIZE));

            List<MountRace.TrackPoint> initialPath = MountRace.createPath(lastLocation, newLocation);
            chunks.addAll(MountRace.getRequiredChunks(initialPath));

            lastLocation = newLocation;
        }

        return new RaceGameArenaLayout() {
            @Override
            public List<CheckPoint> checkPoints() {
                return checkPoints;
            }

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
