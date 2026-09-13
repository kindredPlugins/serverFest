package at.gaderman.serverFest.fests.game.triggers;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class TriggerListener implements Listener {

    private final Fest fest;

    public TriggerListener(Fest fest) {
        this.fest = fest;

        Bukkit.getPluginManager().registerEvents(this, ServerFest.getPlugin());
    }

    public void unregister(){
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        if(!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        fest.onLeave(player);
        player.saveData();
    }

}
