package at.gaderman.serverFest.gui.fests;

import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.definition.GameDefinition;
import at.gaderman.serverFest.fests.schedule.FixedDateScheduled;
import at.gaderman.serverFest.gui.ActionInventory;
import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.eclipse.sisu.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;

public class FestCreationGUI extends ActionInventory {

    private FestType festType = FestType.CASUAL;
    private @Nullable GameType gameType;
    private int minPlayers = 2;
    private int maxPlayers = -1;

    private boolean recurring = false;
    private LocalDateTime time = LocalDateTime.now().plusSeconds(30);
    private ChronoUnit unit = ChronoUnit.WEEKS;
    private int offset = 1;

    public FestCreationGUI() {
        super(36, Component.text("Fest Creation", NamedTextColor.GOLD));
        updateInventory();
    }


    /**
     * 0  1  2  3  4  5  6  7  8
     * 9  10 11 12 13 14 15 16 17
     * 18 19 20 21 22 23 24 25 26
     * 27 28 29 30 31 32 33 34 35
     * <p>
     * -  -  -  -  -  -  -  -  B
     * F  G  Mi MX -  T  Ti U  Of
     * -  -  -  -  -  -  -  -  -
     * -  -  -  A  -  C  -  -  -
     */
    private static final int FEST_TYPE_SLOT = 9;
    private static final int GAME_TYPE_SLOT = 10;
    private static final int MIN_PLAYERS_SLOT = 11;
    private static final int MAX_PLAYERS_SLOT = 12;

    private static final int RECURRING_SLOT = 14;
    private static final int TIME_SLOT = 15;
    private static final int UNIT_SLOT = 16;
    private static final int OFFSET_SLOT = 17;

    private static final int ACCEPT_SLOT = 30;
    private static final int CANCEL_SLOT = 32;

    @Override
    protected void initializeInventory(int size, Component inventoryName) {
        super.initializeInventory(size, inventoryName);

        for (int i = 0; i < size; i++)
            inventory.setItem(i, getFillItem());
    }

    private void updateInventory() {
        updateFestType();
        defineInventoryAction(FEST_TYPE_SLOT, event -> {
            festType = FestType.values()[(festType.ordinal() + 1) % FestType.values().length];
            updateFestType();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);
        updateGameType();
        defineInventoryAction(GAME_TYPE_SLOT, event -> {
            int increment = event.isLeftClick() ? 1 : -1;
            int newIndex = gameType == null ? -1 + increment : gameType.ordinal() + increment;

            if (newIndex == -1)
                gameType = null;
            else if (newIndex < -1)
                gameType = GameType.values()[GameType.values().length - 1];
            else if (newIndex >= GameType.values().length)
                gameType = null;
            else
                gameType = GameType.values()[newIndex];

            updateGameType();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        updateMinPlayerCount();
        defineInventoryAction(MIN_PLAYERS_SLOT, event -> {
            int increment = event.isLeftClick() ? 1 : -1;
            if (event.isShiftClick()) increment *= 10;

            minPlayers = Math.clamp(minPlayers + increment, 2, 99);
            updateMinPlayerCount();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        updateMaxPlayerCount();
        defineInventoryAction(MAX_PLAYERS_SLOT, event -> {
            int increment = event.isLeftClick() ? 1 : -1;
            if (event.isShiftClick()) increment *= 10;

            maxPlayers = Math.clamp(maxPlayers + increment, 2, 99);
            updateMaxPlayerCount();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        updateRecurring();
        defineInventoryAction(RECURRING_SLOT, event -> {
            recurring = !recurring;
            updateRecurring();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        updateTime();
        defineInventoryAction(TIME_SLOT, event -> {
            if (!(event.getWhoClicked() instanceof Player player)) return;

            new DatePickerGUI(time, player, this, newTime -> {
                time = newTime;
                updateTime();
            }).openInventory(player);
        });

        inventory.setItem(ACCEPT_SLOT, ItemStack.of(Material.GREEN_WOOL));
        defineInventoryAction(ACCEPT_SLOT, _ -> {
            FixedDateScheduled.create(festType, gameType, minPlayers, time);
            inventory.close();
        }, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    private void updateFestType() {
        Material type = switch (festType) {
            case CASUAL -> Material.FIREWORK_ROCKET;
            case SERVER_COMPETITION -> Material.DIAMOND;
            case DUEL -> Material.DIAMOND_SWORD;
        };

        ItemStack item = new ItemStack(type);
        item.editMeta(meta -> {
            Component displayName = switch (festType) {
                case CASUAL -> Component.text("Casual Fest", NamedTextColor.GREEN);
                case SERVER_COMPETITION -> Component.text("Server Competition", NamedTextColor.RED);
                case DUEL -> Component.text("Duel", NamedTextColor.BLUE);
            };
            meta.itemName(displayName);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });

        inventory.setItem(FEST_TYPE_SLOT, item);
    }

    private void updateGameType() {
        GameDefinition gameDefinition = gameType == null ? null : gameType.definition();
        Material type = gameDefinition == null ? Material.BARRIER : gameDefinition.iconType();

        ItemStack item = new ItemStack(type);
        item.editMeta(meta -> {
            meta.itemName(gameDefinition == null
                    ? Component.text("Random Game", NamedTextColor.GOLD)
                    : gameDefinition.iconName());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });

        inventory.setItem(GAME_TYPE_SLOT, item);
    }

    private void updateMinPlayerCount() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(meta -> {
            Component itemName = minPlayers > 99
                    ? Component.text("Minimum Players: ", NamedTextColor.GRAY)
                    .append(Component.text("no limit", NamedTextColor.GOLD))
                    : Component.text("Minimum Players: ", NamedTextColor.GRAY)
                    .append(Component.text(minPlayers, NamedTextColor.GOLD));

            meta.itemName(itemName);
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Left to decrease", NamedTextColor.YELLOW),
                    Component.text("Right to increase", NamedTextColor.YELLOW),
                    Component.text("Shift for 10x", NamedTextColor.YELLOW)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            meta.setMaxStackSize(99);
            item.setAmount(Math.clamp(minPlayers, 1, 99));
        });

        inventory.setItem(MIN_PLAYERS_SLOT, item);
    }

    private void updateMaxPlayerCount() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(meta -> {
            Component itemName = maxPlayers == -1
                    ? Component.text("Maximum Players: ", NamedTextColor.GRAY)
                    .append(Component.text("no limit", NamedTextColor.GOLD))
                    : Component.text("Maximum Players: ", NamedTextColor.GRAY)
                    .append(Component.text(maxPlayers, NamedTextColor.GOLD));

            meta.itemName(itemName);
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Left to decrease", NamedTextColor.YELLOW),
                    Component.text("Right to increase", NamedTextColor.YELLOW),
                    Component.text("Shift for 10x", NamedTextColor.YELLOW),
                    Component.text("Set to <2 for no limit", NamedTextColor.GRAY)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            meta.setMaxStackSize(99);
            item.setAmount(Math.clamp(maxPlayers, 1, 99));
        });

        inventory.setItem(MAX_PLAYERS_SLOT, item);
    }

    private void updateRecurring() {
        ItemStack item = ItemStack.of(recurring ? Material.CLOCK : Material.WRITTEN_BOOK);
        item.editMeta(meta -> {
            meta.itemName(Component.text(recurring ? "Recurring" : "One-Time", NamedTextColor.GOLD));
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Click to toggle", NamedTextColor.YELLOW)
            ));
        });
        inventory.setItem(RECURRING_SLOT, item);
    }

    private void updateTime() {
        ItemStack item = ItemStack.of(Material.CLOCK);
        item.editMeta(meta -> {
            meta.itemName(Component.text("Time: ", NamedTextColor.GRAY)
                    .append(Component.text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(time), NamedTextColor.GOLD)));
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Click to change", NamedTextColor.YELLOW)
            ));
        });
        inventory.setItem(TIME_SLOT, item);
    }
}
