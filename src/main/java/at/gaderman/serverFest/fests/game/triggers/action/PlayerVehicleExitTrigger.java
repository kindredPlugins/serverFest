package at.gaderman.serverFest.fests.game.triggers.action;

import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleExitEvent;

public interface PlayerVehicleExitTrigger {
    void onVehicleExit(VehicleExitEvent event, Player player, Vehicle vehicle);
}
