package at.gaderman.serverFest.fests.game.triggers.action;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityToggleGlideEvent;

public interface PlayerToggleGlideTrigger {
    void onToggleGlide(EntityToggleGlideEvent event, Player player);
}
