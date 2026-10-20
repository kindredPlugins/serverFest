package at.gaderman.serverFest.fests.game.triggers;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.triggers.action.PlayerDeathTrigger;
import at.gaderman.serverFest.fests.game.triggers.action.PlayerToggleGlideTrigger;
import at.gaderman.serverFest.fests.game.triggers.action.PlayerVehicleExitTrigger;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import at.gaderman.serverFest.fests.game.triggers.interact.PlayerBlockBreakTrigger;
import at.gaderman.serverFest.fests.game.triggers.interact.PlayerBlockPlaceTrigger;
import at.gaderman.serverFest.fests.game.triggers.interact.PlayerInteractTrigger;
import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerDropItemTrigger;
import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerInventoryClickTrigger;
import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerPickupItemTrigger;
import at.gaderman.serverFest.fests.game.triggers.projectiles.PlayerProjectileHitTrigger;
import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

public class TriggerListener implements Listener {

    private final Fest fest;

    public TriggerListener(Fest fest) {
        this.fest = fest;

        Bukkit.getPluginManager().registerEvents(this, ServerFest.getPlugin());
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        fest.onLeave(player);
        player.saveData();
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementCriterionGrantEvent event){
        Player player = event.getPlayer();
        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerTeleportEnderPearl(PlayerTeleportEvent event){
        if(event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL)
            return;

        Player player = event.getPlayer();
        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        if(event.getTo().getWorld().equals(fest.world()))
            return;

        fest.onEnderPearlTeleportAway(event, player);
    }

    //region Action

    @EventHandler
    public void onPlayerToggleGlide(EntityToggleGlideEvent event) {
        if (!(fest.game() instanceof PlayerToggleGlideTrigger toggleGlideTrigger))
            return;

        if (!(event.getEntity() instanceof Player player))
            return;

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        toggleGlideTrigger.onToggleGlide(event, player);
    }

    @EventHandler
    public void onPlayerExitVehicle(VehicleExitEvent event) {
        if (!(fest.game() instanceof PlayerVehicleExitTrigger playerVehicleExitTrigger))
            return;

        if (!(event.getExited() instanceof Player player))
            return;

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerVehicleExitTrigger.onVehicleExit(event, player, event.getVehicle());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!(fest.game() instanceof PlayerDeathTrigger playerDeathTrigger))
            return;

        Player player = event.getPlayer();

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerDeathTrigger.onPlayerDeath(event, player);
    }

    //endregion

    //region Damage

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(fest.game() instanceof PlayerDamageTrigger playerDamageTrigger))
            return;

        if (!(event.getEntity() instanceof Player player))
            return;

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerDamageTrigger.onPlayerDamage(event, player);
    }

    //endregion

    //region Interact

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!(fest.game() instanceof PlayerBlockPlaceTrigger playerPlaceBlockTrigger))
            return;

        Player player = event.getPlayer();

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerPlaceBlockTrigger.onBlockPlace(event, player);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!(fest.game() instanceof PlayerBlockBreakTrigger playerBlockBreakTrigger))
            return;

        Player player = event.getPlayer();

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerBlockBreakTrigger.onBlockBreak(event, player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!(fest.game() instanceof PlayerInteractTrigger playerInteractTrigger))
            return;

        Player player = event.getPlayer();

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerInteractTrigger.onInteract(event, player);
    }

    //endregion

    //region Inventory

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(fest.game() instanceof PlayerInventoryClickTrigger playerInventoryClickTrigger))
            return;

        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerInventoryClickTrigger.onInventoryClick(event, player);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (!(fest.game() instanceof PlayerDropItemTrigger playerDropItemTrigger))
            return;

        Player player = event.getPlayer();

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerDropItemTrigger.onPlayerDropItem(event, player, event.getItemDrop());
    }

    @EventHandler
    public void onPickUpItem(EntityPickupItemEvent event) {
        if (!(fest.game() instanceof PlayerPickupItemTrigger playerPickupItemTrigger))
            return;

        if (!(event.getEntity() instanceof Player player))
            return;

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerPickupItemTrigger.onPlayerPickupItem(event, player, event.getItem());
    }

    //endregion

    //region Projectiles

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(fest.game() instanceof PlayerProjectileHitTrigger playerProjectileHitTrigger))
            return;

        if (!(event.getEntity().getShooter() instanceof Player player))
            return;

        if (!fest.participantUUIDs().contains(player.getUniqueId()))
            return;

        playerProjectileHitTrigger.onPlayerProjectileHit(event, event.getEntity(), player);
    }

    //endregion
}
