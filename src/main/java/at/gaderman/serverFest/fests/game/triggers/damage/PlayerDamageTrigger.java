package at.gaderman.serverFest.fests.game.triggers.damage;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

public interface PlayerDamageTrigger {
    void onPlayerDamage(EntityDamageEvent event, Player player);
}
