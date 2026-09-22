package at.gaderman.serverFest.fests.game.triggers.prefabs;

import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerDropItemTrigger;
import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerInventoryClickTrigger;
import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerPickupItemTrigger;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Prefab interface which automatically cancels changing items in inventory, dropping and picking them up
 */
public interface NoItemInventoryManipulation extends PlayerInventoryClickTrigger, PlayerDropItemTrigger, PlayerPickupItemTrigger {

    @Override
    default void onInventoryClick(InventoryClickEvent event, Player player){
        event.setCancelled(true);
    }

    @Override
    default void onPlayerDropItem(PlayerDropItemEvent event, Player player, Item item){
        event.setCancelled(true);
    }

    @Override
    default void onPlayerPickupItem(EntityPickupItemEvent event, Player player, Item item){
        event.setCancelled(true);
    }
}
