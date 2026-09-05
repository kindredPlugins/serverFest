package at.gaderman.serverFest.fests.game;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import at.gaderman.serverFest.gui.BasicGUIElement;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class FestGame implements BasicGUIElement {

    protected final Fest fest;

    private final Map<UUID, FestPlayerData> playerDataMap = new HashMap<>();

    public FestGame(Fest fest){
        this.fest = fest;
    }

    /**
     * Called to setup the game specific world, the world itself is already created inside fest, this just initializes it for the game, like adding race tracks or arenas
     */
    public abstract void setUpWorld();

    /**
     * Called when the game is started so usually after the world has been set up
     */
    public abstract void onStart();

    public FestPlayerData getPlayerData(UUID uuid){
        if(playerDataMap.containsKey(uuid))
            return playerDataMap.get(uuid);

        FestPlayerData playerData = createPlayerData(uuid);
        playerDataMap.put(uuid, playerData);
        return playerData;
    }

    protected abstract @NonNull FestPlayerData createPlayerData(UUID uuid);
}
