package at.gaderman.serverFest.fests.game.triggers.interact;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

public interface PlayerBlockBreakTrigger {
    void onBlockBreak(BlockBreakEvent event, Player player);
}
