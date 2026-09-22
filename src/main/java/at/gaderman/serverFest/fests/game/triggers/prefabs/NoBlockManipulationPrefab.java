package at.gaderman.serverFest.fests.game.triggers.prefabs;

import at.gaderman.serverFest.fests.game.triggers.interact.PlayerBlockBreakTrigger;
import at.gaderman.serverFest.fests.game.triggers.interact.PlayerBlockPlaceTrigger;
import at.gaderman.serverFest.fests.game.triggers.interact.PlayerInteractTrigger;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Prefab interface which automatically cancels block placing, breaking and interacting for a FestGame to implement
 */
public interface NoBlockManipulationPrefab extends PlayerBlockPlaceTrigger, PlayerBlockBreakTrigger, PlayerInteractTrigger {

    @Override
    default void onBlockPlace(BlockPlaceEvent event, Player player){
        event.setCancelled(true);
    }

    @Override
    default void onBlockBreak(BlockBreakEvent event, Player player){
        event.setCancelled(true);
    }

    @Override
    default void onInteract(PlayerInteractEvent event, Player player){
        if(event.getClickedBlock() != null)
            event.setCancelled(true);
    }
}
