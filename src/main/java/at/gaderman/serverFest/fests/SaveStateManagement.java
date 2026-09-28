package at.gaderman.serverFest.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.game.data.PlayerMetaData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

public class SaveStateManagement {

    public static final NamespacedKey SAVED_STATE = new NamespacedKey(ServerFest.getPlugin(), "saved_state");
    public static final NamespacedKey SAVE_LOCATION = new NamespacedKey(ServerFest.getPlugin(), "saveLocation");

    /**
     * Completely saves data and stats of a player by encoding it in their ender chest
     */
    public static void savePlayerState(Player player) {
        player.saveData();

        Inventory enderChest = player.getEnderChest();
        ItemStack[] enderChestContent = enderChest.getContents();

        ItemStack enderContainer = transferToContainer(enderChestContent);
        enderContainer.editMeta(meta -> meta.itemName(Component.text("Ender Chest Content", NamedTextColor.DARK_GREEN)));
        enderChest.setItem(0, enderContainer);

        for (int i = 1; i < enderChest.getSize(); i++) {
            enderChest.setItem(i, ItemStack.empty());
        }

        ItemStack[] invContents = player.getInventory().getContents();

        ItemStack[] firstContents = Arrays.copyOfRange(invContents, 0, 27);
        ItemStack[] secondContents = Arrays.copyOfRange(invContents, 27, invContents.length);

        ItemStack firstInvContainer = transferToContainer(firstContents);
        firstInvContainer.editMeta(meta -> meta.itemName(Component.text("Inventory Contents 1", NamedTextColor.AQUA)));

        ItemStack secondInvContainer = transferToContainer(secondContents);
        secondInvContainer.editMeta(meta -> meta.itemName(Component.text("Inventory Contents 2", NamedTextColor.AQUA)));

        //TODO: handle in-hand and crafting items

        enderChest.setItem(1, firstInvContainer);
        enderChest.setItem(2, secondInvContainer);

        player.getInventory().clear();

        enderChest.setItem(3, PlayerMetaData.createPlayerDataItem(player));
        PlayerMetaData.resetPlayer(player);

        enderChest.setItem(4, transferEffectsToPotion(player));
        player.clearActivePotionEffects();

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(SAVED_STATE, PersistentDataType.STRING, ServerFest.getPlugin().getPluginMeta().getVersion());

        pdc.set(SAVE_LOCATION, PersistentDataType.STRING, serializeLocation(player.getLocation()));
    }

    /**
     * Restores the saved data of a player by reading their ender chest and PDC
     */
    public static void restorePlayerState(Player player) {
        Inventory enderChest = player.getEnderChest();

        ItemStack firstInvContainer = enderChest.getItem(1);
        ItemStack secondInvContainer = enderChest.getItem(2);

        ItemStack[] firstContents = retrieveContentsFromContainer(firstInvContainer);
        ItemStack[] secondContents = retrieveContentsFromContainer(secondInvContainer);

        ItemStack[] invContents = Arrays.copyOf(firstContents, 43);
        System.arraycopy(secondContents, 0, invContents, 27, 16);

        player.getInventory().setContents(invContents);

        ItemStack metaDataItem = enderChest.getItem(3);
        if (metaDataItem != null) {
            PlayerMetaData.applyToPlayer(player, metaDataItem);
        }

        ItemStack effectsPotion = enderChest.getItem(4);
        if (effectsPotion != null) {
            transferEffectsFromPotion(effectsPotion, player);
        }

        ItemStack enderContainer = enderChest.getItem(0);
        player.getEnderChest().setContents(retrieveContentsFromContainer(enderContainer));

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(SAVE_LOCATION, PersistentDataType.STRING)) {
            Location saveLoc = deserializeLocation(pdc.get(SAVE_LOCATION, PersistentDataType.STRING));

            if (saveLoc != null)
                player.teleport(saveLoc);
        }

        pdc.remove(SAVED_STATE);
        player.saveData();
    }

    private static ItemStack transferToContainer(ItemStack[] contents) {
        ItemStack container = ItemStack.of(Material.CHEST);
        container.editMeta(meta -> {
            BlockStateMeta blockStateMeta = (BlockStateMeta) meta;
            Chest chest = (Chest) blockStateMeta.getBlockState();
            chest.getBlockInventory().setContents(contents);
            blockStateMeta.setBlockState(chest);
        });

        return container;
    }

    private static ItemStack[] retrieveContentsFromContainer(@Nullable ItemStack container) {
        if (container == null)
            return new ItemStack[0];

        if (!(container.getItemMeta() instanceof BlockStateMeta blockStateMeta))
            return new ItemStack[0];

        if (!(blockStateMeta.getBlockState() instanceof Chest chest))
            return new ItemStack[0];

        return chest.getBlockInventory().getContents();
    }

    //TODO: known issue, active potion effects only covers highest power, dormant lower-power effects will be skipped
    private static ItemStack transferEffectsToPotion(Player player) {
        ItemStack potion = ItemStack.of(Material.POTION);
        potion.editMeta(meta -> {
            PotionMeta potionMeta = (PotionMeta) meta;

            player.getActivePotionEffects().forEach(effect -> potionMeta.addCustomEffect(effect, false));
        });
        return potion;
    }

    private static void transferEffectsFromPotion(ItemStack potion, Player player) {
        if (potion == null || !potion.hasItemMeta())
            return;

        if (!(potion.getItemMeta() instanceof PotionMeta potionMeta))
            return;

        player.clearActivePotionEffects();
        potionMeta.getCustomEffects().forEach(player::addPotionEffect);
    }

    private static String serializeLocation(Location location) {
        return location.getWorld().getUID() + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ() + ":" + location.getYaw() + ":" + location.getPitch();
    }

    private static Location deserializeLocation(String location) {
        try {
            String[] parts = location.split(":");
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }

    public static class SaveListener implements Listener {

        public SaveListener() {
            Bukkit.getPluginManager().registerEvents(this, ServerFest.getPlugin());
        }

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            PersistentDataContainer pdc = player.getPersistentDataContainer();

            if (!pdc.has(SAVED_STATE))
                return;

            restorePlayerState(player);
        }
    }
}
