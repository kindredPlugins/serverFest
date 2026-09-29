package at.gaderman.serverFest.fests.arenas.implementations.race;

import at.gaderman.serverFest.fests.arenas.ArenaDefinition;
import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.implementations.race.ElytraRace;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ElytraRaceArenaDefinition implements ArenaDefinition {

    private Location origin;
    private final List<CheckPoint> checkPoints = new ArrayList<>();

    @Override
    public @NonNull ArenaLayout generateLayout(Location origin) {
        this.origin = origin;

        createElytraTrack();

        Set<FestGame.ChunkPos> chunks = new HashSet<>();

        for (Vector point : trackSamples) {
            int chunkX = ((int) point.getX()) >> 4;
            int chunkZ = ((int) point.getZ()) >> 4;

            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    chunks.add(new FestGame.ChunkPos(chunkX + x, chunkZ + z));
        }

        return new ElytraRaceArenaLayout(origin, checkPoints, trackSamples, segmentStartIndex, chunks);
    }

    // How far the track moves left/right.
    public static final double WAVE_AMPLITUDE = 100;

    private void createElytraTrack() {
        int gateCount = 15;

        double spacing = 50.0;

        double gateSize = ElytraRace.GATE_SIZE;

        checkPoints.add(new CheckPoint(origin, gateSize, gateSize));

        // How quickly it oscillates.
        double waveLength = 2;

        double lastZ = 0;
        for (int i = 1; i < gateCount + 1; i++) {
            double z = lastZ + spacing + Math.random() * 35;
            lastZ = z;

            // Smooth left/right movement.
            double x = Math.sin(i / waveLength) * WAVE_AMPLITUDE;

            // Optional vertical movement.
            double y = Math.random() * 20 - 10;

            Location gateLocation = origin.clone().add(x, y, z);
            checkPoints.add(new CheckPoint(gateLocation, gateSize, gateSize));
        }

        buildTrackSpline();
    }

    private final List<Vector> trackSamples = new ArrayList<>();
    private final List<Integer> segmentStartIndex = new ArrayList<>();

    private static final int SAMPLES_PER_SEGMENT = 32;

    private void buildTrackSpline() {
        trackSamples.clear();
        segmentStartIndex.clear();

        List<Vector> checkPoints = this.checkPoints.stream()
                .map(cp -> cp.location().toVector())
                .toList();

        int n = checkPoints.size();
        for (int i = 0; i < n - 1; i++) {
            segmentStartIndex.add(trackSamples.size());

            Vector p0 = checkPoints.get(Math.max(i - 1, 0));
            Vector p1 = checkPoints.get(i);
            Vector p2 = checkPoints.get(i + 1);
            Vector p3 = checkPoints.get(Math.min(i + 2, n - 1));

            for (int s = 0; s < SAMPLES_PER_SEGMENT; s++) {
                trackSamples.add(catmullRom(p0, p1, p2, p3, s / (double) SAMPLES_PER_SEGMENT));
            }
        }
        trackSamples.add(checkPoints.get(n - 1));
        segmentStartIndex.add(trackSamples.size() - 1);
    }

    private Vector catmullRom(Vector p0, Vector p1, Vector p2, Vector p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        double x = 0.5 * ((2 * p1.getX()) + (-p0.getX() + p2.getX()) * t + (2 * p0.getX() - 5 * p1.getX() + 4 * p2.getX() - p3.getX()) * t2 + (-p0.getX() + 3 * p1.getX() - 3 * p2.getX() + p3.getX()) * t3);
        double y = 0.5 * ((2 * p1.getY()) + (-p0.getY() + p2.getY()) * t + (2 * p0.getY() - 5 * p1.getY() + 4 * p2.getY() - p3.getY()) * t2 + (-p0.getY() + 3 * p1.getY() - 3 * p2.getY() + p3.getY()) * t3);
        double z = 0.5 * ((2 * p1.getZ()) + (-p0.getZ() + p2.getZ()) * t + (2 * p0.getZ() - 5 * p1.getZ() + 4 * p2.getZ() - p3.getZ()) * t2 + (-p0.getZ() + 3 * p1.getZ() - 3 * p2.getZ() + p3.getZ()) * t3);
        return new Vector(x, y, z);
    }
}
