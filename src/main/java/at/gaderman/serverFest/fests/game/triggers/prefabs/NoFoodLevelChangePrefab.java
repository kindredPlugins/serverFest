package at.gaderman.serverFest.fests.game.triggers.prefabs;

import at.gaderman.serverFest.fests.game.triggers.passive.FoodLevelChangeTrigger;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public interface NoFoodLevelChangePrefab extends FoodLevelChangeTrigger {
    @Override
    default void onFoodLevelChange(FoodLevelChangeEvent event, Player player){
        event.setCancelled(true);
    }
}
