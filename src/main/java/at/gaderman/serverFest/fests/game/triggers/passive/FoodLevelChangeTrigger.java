package at.gaderman.serverFest.fests.game.triggers.passive;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public interface FoodLevelChangeTrigger {
    void onFoodLevelChange(FoodLevelChangeEvent event, Player player);
}
