package at.gaderman.serverFest.fests.arenas;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.management.WorldManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import static at.gaderman.serverFest.fests.Fest.FEST_OFFSET;

public class ArenaManager {

    private final HashMap<GameType, ArenaLayout> arenaLayouts = new HashMap<>();
    public final List<FestGame.ChunkPos> chunksToGenerate = new ArrayList<>();

    private World festWorld;
    private ScheduledTask generationTask;

    private boolean generationBatchFinished = true;

    public void reserveArenas() {
        Arrays.stream(GameType.values()).forEach(this::requestArenaGeneration);
    }

    public void requestArenaGeneration(GameType gameType) {
        festWorld = WorldManager.getInstance().loadOrCreateFestWorld();
        PersistentDataContainer pdc = festWorld.getPersistentDataContainer();
        long offset = pdc.getOrDefault(FEST_OFFSET, PersistentDataType.LONG, 0L);

        Location origin = new Location(festWorld, offset, festWorld.getSeaLevel(), gameType.ordinal() * 10_000);
        origin.getChunk().addPluginChunkTicket(ServerFest.getPlugin());
        ArenaLayout arenaLayout = gameType.getArenaDefinition().generateLayout(origin);
        arenaLayouts.put(gameType, arenaLayout);
        chunksToGenerate.addAll(arenaLayout.chunks());

        schedulePreGeneration();
    }

    public ArenaLayout requestArenaLayout(GameType gameType) {
        if (!arenaLayouts.containsKey(gameType)) {
            requestArenaGeneration(gameType);
        }

        ArenaLayout arenaLayout = arenaLayouts.get(gameType);
        arenaLayouts.remove(gameType);
        requestArenaGeneration(gameType);
        return arenaLayout;
    }

    private void schedulePreGeneration() {
        if (generationTask != null)
            return;

        Logger logger = ServerFest.getPlugin().getLogger();
        logger.info("Starting fest arena terrain pre-generation...");

        generationTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            if (chunksToGenerate.isEmpty()) {
                task.cancel();
                generationTask = null;

                logger.info("Finished fest arena terrain pre-generation for the time being");
                return;
            }

            if(!generationBatchFinished)
                return;

            //logger.info("Current tick times: " + Arrays.toString(Bukkit.getTickTimes()));

            generationBatchFinished = false;
            FestGame.ChunkPos chunk = chunksToGenerate.removeFirst();
            festWorld.getChunkAtAsync(chunk.x(), chunk.z()).thenRun(() -> generationBatchFinished = true);
            //logger.info("Pre-generating arena terrain [" + chunksToGenerate.size() + " remaining]");

        }, 1, 1);
    }

}
