package at.gaderman.serverFest.fests.game.triggers.interact;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

public interface PlayerBlockPlaceTrigger {
    void onBlockPlace(BlockPlaceEvent event, Player player);
}
