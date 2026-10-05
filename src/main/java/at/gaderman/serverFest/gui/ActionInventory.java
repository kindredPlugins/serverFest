package at.gaderman.serverFest.gui;

import at.gaderman.serverFest.ServerFest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class ActionInventory implements InventoryHolder, Listener {
    protected Inventory inventory;
    private Component deferredInventoryName = null;

    private final Map<Integer, Consumer<InventoryClickEvent>> inventoryActionsMap = new HashMap<>();

    public ActionInventory(int size) {
        initializeInventory(size, null);
    }

    public ActionInventory(int size, Component inventoryName) {
        initializeInventory(size, inventoryName);
    }

    public ActionInventory(){ this.deferredInventoryName = null; }

    public ActionInventory(Component inventoryName){ this.deferredInventoryName = inventoryName; }

    protected int calculateSize(){
        return 27;
    }

    protected void initializeInventory(int size, Component inventoryName) {
        if (inventory != null) return;

        if(inventoryName == null)
            this.inventory = Bukkit.createInventory(this, size);

        else
            this.inventory = Bukkit.createInventory(this, size, inventoryName);
    }

    public void defineInventoryAction(int slot, Consumer<InventoryClickEvent> action) {
        inventoryActionsMap.put(slot, action);
    }

    public void defineInventoryAction(int slot, Consumer<InventoryClickEvent> action, Sound sound, float volume, float pitch) {
        defineInventoryAction(slot, action
                .andThen(event -> ((Player) event.getWhoClicked()).playSound(event.getWhoClicked(), sound, volume, pitch)));
    }

    public ItemStack getFillItem() {
        ItemStack item = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    protected void fillWithFillItem(){
        for(int i = 0; i < inventory.getSize(); i++)
            inventory.setItem(i, getFillItem());
    }

    protected void fillEmptyWithClearFill(){
        for(int i = 0; i < inventory.getSize(); i++){
            ItemStack presentItem = inventory.getItem(i);
            if(presentItem == null || presentItem.isEmpty())
                inventory.setItem(i, getFillItem().withType(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
        }
    }

    public ItemStack getBackItem(){
        ItemStack backItem = ItemStack.of(Material.ARROW);
        backItem.editMeta(meta -> {
            meta.itemName(Component.text("Back"));
        });
        return backItem;
    }

    public void openInventory(Player player) {
        // Ensure inventory is created now that subclass construction has finished
        if (inventory == null)
            initializeInventory(calculateSize(), deferredInventoryName);

        if (Objects.equals(player.getOpenInventory().getTopInventory().getHolder(), this)) return;

        if(inventory.getViewers().isEmpty())
            Bukkit.getPluginManager().registerEvents(this, ServerFest.getPlugin());

        player.openInventory(getInventory());
    }

    protected void closeAction(){}

    protected boolean canShiftIntoInv(){ return false; }

    @Override
    public @NotNull Inventory getInventory() {
        if(inventory == null) initializeInventory(calculateSize(), deferredInventoryName);
        return inventory;
    }

    @EventHandler
    public final void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() == null) return;
        if (!event.getInventory().getHolder().equals(this)) return;

        if (inventory.getViewers().size() > 1) return;

        HandlerList.unregisterAll(this);
        closeAction();
    }

    @EventHandler
    public final void onAction(InventoryClickEvent event) {
        if (event.getInventory().getHolder() == null) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getInventory().getHolder().equals(this)) return;

        if (!Objects.equals(Objects.requireNonNull(event.getClickedInventory()).getHolder(), this)) return;

        if(event.getClick().isCreativeAction() && event.getWhoClicked().getGameMode() == GameMode.CREATIVE) return;

        event.setCancelled(true);
        if (!inventoryActionsMap.containsKey(event.getSlot())) return;

        inventoryActionsMap.get(event.getSlot()).accept(event);
    }

    @EventHandler
    public final void onDrag(InventoryInteractEvent event) {
        if (event.getInventory().getHolder() == null) return;
        if (!event.getInventory().getHolder().equals(this)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public final void onShiftIntoInv(InventoryClickEvent event){
        if(canShiftIntoInv() || !event.isShiftClick()) return;

        if (event.getInventory().getHolder() == null) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getInventory().getHolder().equals(this)) return;

        if(!event.getClickedInventory().getHolder().equals(event.getView().getPlayer())) return;
        event.setCancelled(true);
    }
}
