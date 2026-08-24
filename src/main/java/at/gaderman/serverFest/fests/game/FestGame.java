package at.gaderman.serverFest.fests.game;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.gui.BasicGUIElement;

public abstract class FestGame implements BasicGUIElement {

    protected final Fest fest;

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
}
