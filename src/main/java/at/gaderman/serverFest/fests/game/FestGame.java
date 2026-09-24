package at.gaderman.serverFest.fests.game;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import at.gaderman.serverFest.gui.BasicGUIElement;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public abstract class FestGame implements BasicGUIElement {

    protected final Fest fest;

    private boolean running;
    private final List<Player> winners = new ArrayList<>();

    private final Map<UUID, FestPlayerData> playerDataMap = new HashMap<>();

    public FestGame(Fest fest) {
        this.fest = fest;
    }

    public Location determineOrigin(){
        return new Location(fest.world(), fest.offset(), fest.world().getSeaLevel(), 0).toCenterLocation();
    }

    /**
     * Called to setup the game specific world, the world itself is already created inside fest, this just initializes it for the game, like adding race tracks or arenas
     * @return a runnable callback when world setup has been completed
     */
    public abstract CompletableFuture<Void> setUpWorld();

    /**
     * Called when fest participants have been teleported to the fest world, usually used to create a starting are for them to gather
     */
    public abstract void onPlayersJoinedWorld();

    /**
     * Start the game, setting its status to running and calling the implementing games {@link #onStart()}
     */
    public final void start() {
        running = true;
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
            if(festPlayerData != null)
                festPlayerData.reset();
        });
        fest.stop();
        //TODO: allow winner to pick reward
    }

    protected abstract void onStop();

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
}
