package at.gaderman.serverFest.fests.game.triggers.interact;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

public interface PlayerInteractTrigger {
    void onInteract(PlayerInteractEvent event, Player player);
}
