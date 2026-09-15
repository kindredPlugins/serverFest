package at.gaderman.serverFest.fests.game.triggers.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public interface PlayerInventoryClickTrigger {
    void onInventoryClick(InventoryClickEvent event, Player player);
}
