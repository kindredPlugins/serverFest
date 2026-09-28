package at.gaderman.serverFest.fests.game;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import at.gaderman.serverFest.gui.BasicGUIElement;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class FestGame<T extends ArenaLayout> implements BasicGUIElement {

    protected final Fest fest;
    protected final ArenaInstance arenaInstance;
    protected final T arenaLayout;

    private boolean running;
    private final List<Player> winners = new ArrayList<>();
    private BossBar arenaBuildingBar;

    private final Map<UUID, FestPlayerData> playerDataMap = new HashMap<>();

    public FestGame(Fest fest, ArenaInstance arenaInstance) {
        this.fest = fest;
        this.arenaInstance = arenaInstance;
        this.arenaLayout = (T) arenaInstance.arenaLayout();
    }

    public Location determineOrigin() {
        return arenaLayout.origin();
    }

    /**
     * Called to setup the game specific world, the world itself is already created inside fest, this just initializes it for the game, like adding race tracks or arenas
     *
     * @return a runnable callback when world setup has been completed
     */
    public abstract CompletableFuture<Void> setUpWorld();

    /**
     * Called when fest participants have been teleported to the fest world, usually used to create a starting are for them to gather
     */
    public void onPlayersJoinedWorld() {
        updateArenaBuildingBar(Component.text("Setting up arena..."), 0.0f);
    }

    /**
     * Start the game, setting its status to running and calling the implementing games {@link #onStart()}
     */
    public final void start() {
        running = true;
        if (arenaBuildingBar != null)
            arenaBuildingBar.viewers().forEach(viewer -> arenaBuildingBar.removeViewer((Audience) viewer));

        onStart();
    }

    /**
     * Called when the game is started so usually after the world has been set up
     */
    protected abstract void onStart();

    /**
     * Called by the underlying fest game when it declares one or multiple winner, will then trigger {@link #onWinner(List)}
     */
    protected final void declareWinner(List<Player> winners) {
        this.winners.addAll(winners);
        running = false;
        onWinner(winners);
    }

    /**
     * The real after winner implementation unique to each fest, at this point the game is no longer running, so it mostly follows
     * with a sort of transition before the game is stopped truly
     */
    protected abstract void onWinner(List<Player> winner);

    /**
     * Stop the game either forcefully or because the underlying fest has completed
     */
    public void stop() {
        running = false;
        onStop();

        fest.participants().forEach(participant -> {
            FestPlayerData festPlayerData = playerDataMap.getOrDefault(participant.getUniqueId(), null);
            if (festPlayerData != null)
                festPlayerData.reset();
        });
        fest.stop();
        reset();
        //TODO: allow winner to pick reward
    }

    protected abstract void onStop();

    private void reset() {
        arenaInstance.markAsReset();
        onReset().thenRun(arenaInstance::resetToReady);
    }

    /**
     * The underlying reset method of each fest game, this will reset the arena so it can then be used for preceding games as well
     */
    protected abstract CompletableFuture<Void> onReset();

    /**
     * Gets or creates player data for this fest for the given UUID
     *
     * @return A fresh FestPlayerData if none existed or the ongoing data of the player participant
     */
    public FestPlayerData getOrCreatePlayerData(UUID uuid) {
        if (playerDataMap.containsKey(uuid))
            return playerDataMap.get(uuid);

        FestPlayerData playerData = createPlayerData(uuid);
        playerDataMap.put(uuid, playerData);
        return playerData;
    }

    protected abstract @NonNull FestPlayerData createPlayerData(UUID uuid);

    public boolean isRunning() {
        return running;
    }

    //region Helper methods

    protected void updateArenaBuildingBar(Component text, float progress) {
        if (arenaBuildingBar == null) {
            arenaBuildingBar = BossBar.bossBar(text, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

            fest.participants().forEach(participant -> arenaBuildingBar.addViewer(participant));
        }

        arenaBuildingBar.name(text);
        arenaBuildingBar.progress(progress);
    }

    protected void loadChunks(World world, Set<ChunkPos> chunks, Consumer<Set<Chunk>> finished) {
        if (chunks.isEmpty()) {
            finished.accept(Collections.emptySet());
            return;
        }

        Set<Chunk> resolvedChunks = new LinkedHashSet<>();
        AtomicInteger remaining = new AtomicInteger(chunks.size());

        for (ChunkPos chunk : chunks) {
            world.getChunkAtAsync(chunk.x(), chunk.z(), true, resolved -> {
                resolvedChunks.add(resolved);

                updateArenaBuildingBar(Component.text("Loading chunks ", NamedTextColor.YELLOW)
                                .append(Component.text(chunks.size() - remaining.get(), NamedTextColor.GOLD))
                                .append(Component.text(" / ", NamedTextColor.GRAY))
                                .append(Component.text(chunks.size(), NamedTextColor.DARK_GRAY))
                        , (float) (chunks.size() - remaining.get()) / chunks.size());

                if (remaining.decrementAndGet() == 0)
                    finished.accept(resolvedChunks);
            });
        }
    }

    //endregion

    public record ChunkPos(int x, int z) {
    }
}
