package at.gaderman.serverFest.fests.game.triggers.inventory;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;

public interface PlayerDropItemTrigger {
    void onPlayerDropItem(PlayerDropItemEvent event, Player player, Item item);
}
