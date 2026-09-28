package at.gaderman.serverFest.fests.arenas;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.GameType;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class ArenaInstance {

    private final GameType gameType;
    private final Location origin;

    private ArenaState state;
    private ArenaDefinition arenaDefinition;
    private ArenaLayout arenaLayout;

    private boolean generationBatchFinished = true;

    public ArenaInstance(GameType gameType, Location origin) {
        this.gameType = gameType;
        this.origin = origin;
        this.state = ArenaState.WAITING;
    }

    public GameType gameType(){
        return this.gameType;
    }

    public ArenaState arenaState(){
        return this.state;
    }

    public ArenaLayout arenaLayout(){
        return this.arenaLayout;
    }

    public CompletableFuture<Void> generateArena(){
        if(state == ArenaState.GENERATING || state == ArenaState.READY || state == ArenaState.ACTIVE)
            return CompletableFuture.completedFuture(null);

        Logger logger = ServerFest.getPlugin().getLogger();

        state = ArenaState.GENERATING;
        logger.info("Starting arena generation for " + gameType.name() + " at [" + origin.x() + " | " + origin.z() + "]");

        arenaDefinition = gameType.getArenaDefinition();
        arenaLayout = arenaDefinition.generateLayout(origin);

        List<FestGame.ChunkPos> chunksToGenerate = new ArrayList<>(arenaLayout.chunks());
        chunksToGenerate.removeIf(chunkPos -> origin.getWorld().isChunkGenerated(chunkPos.x(), chunkPos.z()));

        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            if(chunksToGenerate.isEmpty()){
                task.cancel();

                logger.info("Arena generation finished for " + gameType.name() + " at [" + origin.x() + " | " + origin.z() + "]");
                state = ArenaState.READY;
                future.complete(null);
                return;
            }

            if(!generationBatchFinished)
                return;

            //logger.info("Current tick times: " + Arrays.toString(Bukkit.getTickTimes()));

            generationBatchFinished = false;
            FestGame.ChunkPos chunk = chunksToGenerate.removeFirst();
            origin.getWorld().getChunkAtAsync(chunk.x(), chunk.z()).thenRun(() -> generationBatchFinished = true);
        }, 1, 1);
        return future;
    }

    public boolean activateArena(){
        if(state != ArenaState.READY)
            return false;

        state = ArenaState.ACTIVE;
        return true;
    }

    public void markAsReset(){
        state = ArenaState.RESET;
    }

    public void resetToReady(){
        arenaLayout = arenaDefinition.generateLayout(origin);
        state = ArenaState.READY;
    }
}
