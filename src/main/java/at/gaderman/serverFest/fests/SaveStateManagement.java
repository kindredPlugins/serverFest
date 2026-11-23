package at.gaderman.serverFest.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.duel.DuelBet;
import at.gaderman.serverFest.fests.game.data.PlayerMetaData;
import at.gaderman.serverFest.utils.ItemUtils;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.PotionContents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SaveStateManagement {

    public static final NamespacedKey SAVED_STATE = new NamespacedKey(ServerFest.getPlugin(), "saved_state");
    public static final NamespacedKey SAVE_LOCATION = new NamespacedKey(ServerFest.getPlugin(), "saveLocation");
    public static final NamespacedKey SAVE_VELOCITY = new NamespacedKey(ServerFest.getPlugin(), "saveVelocity");

    private static final int ENDER_CHEST_SLOT = 0;
    private static final int FIRST_INV_SLOT = 1;
    private static final int SECOND_INV_SLOT = 2;
    private static final int PLAYER_DATA_SLOT = 3;
    private static final int EFFECTS_SLOT = 4;
    private static final int BET_SLOT = 9;

    //region PlayerSave

    /// @see #savePlayerState(Player, DuelBet)
    public static void savePlayerState(Player player) {
        savePlayerState(player, null);
    }

    /**
     * Completely saves data and stats of a player by encoding it in their ender chest
     * Can be passed a {@link DuelBet} which saves and separates the bet
     */
    public static void savePlayerState(Player player, @Nullable DuelBet duelBet) {
        player.saveData();

        Inventory enderChest = player.getEnderChest();
        ItemStack[] enderChestContent = enderChest.getContents();

        ItemStack enderContainer = transferToContainer(enderChestContent);
        enderContainer.editMeta(meta -> meta.itemName(Component.text("Ender Chest Content", NamedTextColor.DARK_GREEN)));
        enderChest.setItem(ENDER_CHEST_SLOT, enderContainer);

        for (int i = 1; i < enderChest.getSize(); i++) {
            enderChest.setItem(i, ItemStack.empty());
        }

        if (duelBet != null) {
            boolean isChallenger = duelBet.challenger().equals(player);

            //TODO: potential leak when player is neither opponent nor challenger? cannot throw exception here or save is interrupted!!!

            Economy economy = ServerFest.getPlugin().getEconomy();
            if (economy != null)
                economy.withdrawPlayer(player, isChallenger ? duelBet.challengerCurrencyBet() : duelBet.opponentCurrencyBet());

            List<ItemStack> bet = isChallenger ? duelBet.challengerItems() : duelBet.opponentItems();
            ItemUtils.removeExactly(player, bet);

            ItemStack betContainer = transferToContainer(Arrays.copyOfRange(bet.toArray(new ItemStack[0]), 0, 27));
            enderChest.setItem(BET_SLOT, betContainer);
        }

        ItemStack[] invContents = player.getInventory().getContents();
        ItemStack[] firstContents = Arrays.copyOfRange(invContents, 0, 27);

        List<ItemStack> secondItems = new ArrayList<>(
                Arrays.asList(Arrays.copyOfRange(invContents, 27, invContents.length))); // rest of storage + armor + offhand

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top instanceof CraftingInventory crafting && top.getSize() == 5) {
            ItemStack[] matrix = crafting.getMatrix();
            for (ItemStack item : matrix) {
                if (item != null && !item.getType().isAir()) secondItems.add(item);
            }
            crafting.setMatrix(new ItemStack[matrix.length]);
        }

        ItemStack cursor = player.getItemOnCursor();
        if (!cursor.getType().isAir()) {
            secondItems.add(cursor);
            player.setItemOnCursor(null);
        }

        ItemStack[] secondContents = secondItems.toArray(new ItemStack[0]);

        ItemStack firstInvContainer = transferToContainer(firstContents);
        firstInvContainer.editMeta(meta -> meta.itemName(Component.text("Inventory Contents 1", NamedTextColor.AQUA)));

        ItemStack secondInvContainer = transferToContainer(secondContents);
        secondInvContainer.editMeta(meta -> meta.itemName(Component.text("Inventory Contents 2", NamedTextColor.AQUA)));

        enderChest.setItem(FIRST_INV_SLOT, firstInvContainer);
        enderChest.setItem(SECOND_INV_SLOT, secondInvContainer);

        player.getInventory().clear();

        enderChest.setItem(PLAYER_DATA_SLOT, PlayerMetaData.createPlayerDataItem(player));
        PlayerMetaData.resetPlayer(player);

        enderChest.setItem(EFFECTS_SLOT, transferEffectsToPotion(player));
        player.clearActivePotionEffects();

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(SAVED_STATE, PersistentDataType.STRING, ServerFest.getPlugin().getPluginMeta().getVersion());

        pdc.set(SAVE_LOCATION, PersistentDataType.STRING, serializeLocation(player.getLocation()));
        pdc.set(SAVE_VELOCITY, PersistentDataType.STRING, serializeVelocity(player.getVelocity()));
    }

    public static void restorePlayerState(Player player) {
        restorePlayerState(player, true);
    }

    /**
     * Restores the saved data of a player by reading their ender chest and PDC
     */
    public static void restorePlayerState(Player player, boolean recoverBet) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(SAVE_LOCATION, PersistentDataType.STRING)) {
            Location saveLoc = deserializeLocation(pdc.get(SAVE_LOCATION, PersistentDataType.STRING));

            if (saveLoc != null)
                player.teleport(saveLoc);

        }

        if (pdc.has(SAVE_VELOCITY, PersistentDataType.STRING)) {
            Vector velocity = deserializeVelocity(pdc.get(SAVE_VELOCITY, PersistentDataType.STRING));

            if (velocity != null) player.setVelocity(velocity);
            pdc.remove(SAVE_VELOCITY);
        }

        Inventory enderChest = player.getEnderChest();

        ItemStack firstInvContainer = enderChest.getItem(FIRST_INV_SLOT);
        ItemStack secondInvContainer = enderChest.getItem(SECOND_INV_SLOT);

        ItemStack[] firstContents = retrieveContentsFromContainer(firstInvContainer);
        ItemStack[] secondContents = retrieveContentsFromContainer(secondInvContainer);

        ItemStack[] invContents = new ItemStack[41];
        System.arraycopy(firstContents, 0, invContents, 0, Math.min(27, firstContents.length));
        System.arraycopy(secondContents, 0, invContents, 27, Math.min(14, secondContents.length));

        player.getInventory().setContents(invContents);

        for (int i = 14; i < secondContents.length; i++) {
            ItemStack extra = secondContents[i];
            if (extra == null || extra.getType().isAir()) continue;

            player.getInventory().addItem(extra).forEach((index, item) -> {
                Item drop = player.getWorld().dropItem(player.getLocation(), item);
                drop.setOwner(player.getUniqueId());
            });
        }

        if (recoverBet) {
            ItemStack[] betContents = retrieveContentsFromContainer(enderChest.getItem(BET_SLOT));
            if (betContents.length > 0) {
                var contents = Arrays.stream(betContents).filter(Objects::nonNull).toList();
                player.give(contents, true).drops().forEach(drop -> drop.setOwner(player.getUniqueId()));
            }
        }

        ItemStack metaDataItem = enderChest.getItem(PLAYER_DATA_SLOT);
        if (metaDataItem != null) {
            PlayerMetaData.applyToPlayer(player, metaDataItem);
            player.teleportAsync(player.getLocation()).thenRun(() -> PlayerMetaData.applyToPlayer(player, metaDataItem));
        }

        ItemStack effectsPotion = enderChest.getItem(EFFECTS_SLOT);
        if (effectsPotion != null) {
            transferEffectsFromPotion(effectsPotion, player);
        }

        ItemStack enderContainer = enderChest.getItem(ENDER_CHEST_SLOT);
        player.getEnderChest().setContents(retrieveContentsFromContainer(enderContainer));

        pdc.remove(SAVED_STATE);
        player.saveData();
    }

    //endregion

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

    /**
     * Creates a potion itemStack representing all potion effects of the player. Due to the Bukkit API circumventing
     * same type effects, this method instead uses {@link PotionContents} to also include hidden effects.
     * If it is possible to package more than one hidden effect this method may fail to deliver...
     */
    private static ItemStack transferEffectsToPotion(Player player) {
        ItemStack potion = ItemStack.of(Material.POTION);
        PotionContents.Builder builder = PotionContents.potionContents();

        player.getActivePotionEffects().forEach(effect -> {
            PotionEffect hiddenEffect = effect.getHiddenPotionEffect();
            if (hiddenEffect != null) {
                PotionEffect newEffect = new PotionEffect(hiddenEffect.getType(), hiddenEffect.getDuration(), hiddenEffect.getAmplifier(), hiddenEffect.isAmbient(), hiddenEffect.hasParticles(), hiddenEffect.hasIcon());
                builder.addCustomEffect(newEffect);
            }
            builder.addCustomEffect(effect);
        });
        potion.setData(DataComponentTypes.POTION_CONTENTS, builder.build());
        return potion;
    }

    private static void transferEffectsFromPotion(ItemStack potion, Player player) {
        player.clearActivePotionEffects();

        if (potion == null || !potion.hasItemMeta())
            return;

        if (!(potion.getItemMeta() instanceof PotionMeta potionMeta))
            return;

        potionMeta.getAllEffects().forEach(effect -> {
            if (effect.getHiddenPotionEffect() != null)
                player.addPotionEffect(effect.getHiddenPotionEffect());

            player.addPotionEffect(effect);
        });
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

    private static String serializeVelocity(Vector vector) {
        return vector.getX() + ":" + vector.getY() + ":" + vector.getZ();
    }

    private static Vector deserializeVelocity(String velocity) {
        try {
            String[] parts = velocity.split(":");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            return new Vector(x, y, z);
        } catch (Exception ex) {
            return null;
        }
    }

    public static void updateSaveLocation(@NotNull Player player, @NotNull Location newLocation) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(SAVED_STATE, PersistentDataType.STRING, ServerFest.getPlugin().getPluginMeta().getVersion());

        pdc.set(SAVE_LOCATION, PersistentDataType.STRING, serializeLocation(newLocation));
    }

    //endregion

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
