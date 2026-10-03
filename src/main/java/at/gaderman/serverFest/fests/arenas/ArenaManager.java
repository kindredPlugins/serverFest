package at.gaderman.serverFest.fests.arenas;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.management.WorldManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static at.gaderman.serverFest.fests.Fest.OFFSET_INCREMENT;

public class ArenaManager {

    private final List<ArenaInstance> arenas = new ArrayList<>();
    private final World festWorld;

    private ScheduledTask arenaGenerationTask;
    private ArenaInstance generatingInstance;

    public ArenaManager() {
        festWorld = WorldManager.getInstance().loadOrCreateFestWorld();
        setUp();
    }

    private void setUp() {
        for (int i = 0; i < 5; i++) {
            Arrays.stream(GameType.values()).forEach(this::createArena);
        }
    }

    private void createArena(GameType gameType) {
        long arenaCount = arenas.stream().filter(arena -> arena.gameType().equals(gameType)).count();
        long offset = arenaCount * OFFSET_INCREMENT;
        Location origin = new Location(festWorld, offset, festWorld.getSeaLevel(), gameType.ordinal() * OFFSET_INCREMENT);

        ArenaInstance arena = new ArenaInstance((int) arenaCount, gameType, origin);
        arenas.add(arena);

        scheduleArenaGeneration();
    }

    public @Nullable ArenaInstance requestArena(GameType gameType) {
        List<ArenaInstance> readyArenas = arenas.stream()
                .filter(arena -> arena.gameType().equals(gameType) && arena.arenaState() == ArenaState.READY)
                .toList();

        if (readyArenas.isEmpty())
            return null;

        return readyArenas.get((int) (Math.random() * readyArenas.size()));
    }

    private void scheduleArenaGeneration() {
        if (arenaGenerationTask != null)
            return;

        arenaGenerationTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            if (generatingInstance == null) {
                generatingInstance = arenas.stream().filter(arena -> arena.arenaState() == ArenaState.WAITING).findFirst().orElse(null);

                if (generatingInstance == null) {
                    task.cancel();
                    arenaGenerationTask = null;
                    return;
                }

                generatingInstance.generateArena().thenRun(() -> generatingInstance = null);
            }
        }, 1, 1);
    }
}
