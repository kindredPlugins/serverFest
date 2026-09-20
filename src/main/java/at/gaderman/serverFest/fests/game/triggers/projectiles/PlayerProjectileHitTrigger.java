package at.gaderman.serverFest.fests.game.triggers.projectiles;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;

public interface PlayerProjectileHitTrigger {
    void onPlayerProjectileHit(ProjectileHitEvent event, Projectile projectile, Player player);
}
