package at.gaderman.serverFest.fests.game.triggers.inventory;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;

public interface PlayerPickupItemTrigger {
    void onPlayerPickupItem(EntityPickupItemEvent event, Player player, Item item);
}
