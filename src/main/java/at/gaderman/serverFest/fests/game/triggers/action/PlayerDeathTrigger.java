package at.gaderman.serverFest.fests.game.triggers.action;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

public interface PlayerDeathTrigger {
    void onPlayerDeath(PlayerDeathEvent event, Player player);
}
