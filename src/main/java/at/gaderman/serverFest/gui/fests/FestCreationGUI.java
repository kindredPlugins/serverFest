package at.gaderman.serverFest.gui.fests;

import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.definition.GameDefinition;
import at.gaderman.serverFest.fests.schedule.FixedDateScheduled;
import at.gaderman.serverFest.fests.schedule.RecurringDateScheduled;
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

    private static final FestType[] FEST_TYPES = FestType.values();
    private static final GameType[] GAME_TYPES = GameType.values();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
    private static final ChronoUnit[] UNITS = new ChronoUnit[]{ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS};

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

    private FestType festType = FestType.CASUAL;
    private @Nullable GameType gameType;
    private int minPlayers = 2;
    private int maxPlayers = -1;

    private boolean recurring;
    private LocalDateTime time = LocalDateTime.now().plusSeconds(30);

    // Will be used once recurring scheduling is wired in.
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
     * F  G  Mi MX -  R  T  U  O
     * -  -  -  -  -  -  -  -  -
     * -  -  -  A  -  C  -  -  -
     */

    @Override
    protected void initializeInventory(int size, Component inventoryName) {
        super.initializeInventory(size, inventoryName);

        for (int i = 0; i < size; i++)
            inventory.setItem(i, getFillItem());

        defineInventoryAction(FEST_TYPE_SLOT, event -> {
            int increment = event.isRightClick() ? -1 : 1;
            festType = FEST_TYPES[Math.floorMod(festType.ordinal() + increment, FEST_TYPES.length)];
            updateFestType();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        defineInventoryAction(GAME_TYPE_SLOT, event -> {
            int increment = event.isRightClick() ? -1 : 1;

            if (gameType == null) {
                gameType = increment > 0 ? GAME_TYPES[0] : null;
                if (increment < 0)
                    gameType = GAME_TYPES[GAME_TYPES.length - 1];
            } else {
                int index = gameType.ordinal() + increment;

                if (index < 0)
                    gameType = null;
                else if (index >= GAME_TYPES.length)
                    gameType = null;
                else
                    gameType = GAME_TYPES[index];
            }

            updateGameType();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        defineInventoryAction(MIN_PLAYERS_SLOT, event -> {
            int increment = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) increment *= 10;

            minPlayers = Math.clamp(minPlayers + increment, 2, 99);
            updateMinPlayerCount();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        defineInventoryAction(MAX_PLAYERS_SLOT, event -> {
            int increment = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) increment *= 10;

            int value = maxPlayers + increment;
            maxPlayers = value < 2 ? -1 : Math.clamp(value, 2, 99);

            updateMaxPlayerCount();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        defineInventoryAction(RECURRING_SLOT, _ -> {
            recurring = !recurring;
            updateInventory();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        defineInventoryAction(TIME_SLOT, event -> {
            if (!(event.getWhoClicked() instanceof Player player)) return;

            new DatePickerGUI(time, player, this, newTime -> {
                time = newTime;
                updateTime();
            }).openInventory(player);
        });

        defineInventoryAction(UNIT_SLOT, event -> {
            if (!recurring) return;

            //TODO: ordinal does not much with array order here, is kinda confusing and no overflow cause of that
            int increment = event.isRightClick() ? -1 : 1;
            unit = UNITS[Math.floorMod(unit.ordinal() + increment, UNITS.length)];
            updateUnit();

            if(event.getWhoClicked() instanceof Player player)
                player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1, 1);
        });

        defineInventoryAction(OFFSET_SLOT, event -> {
            if (!recurring) return;

            int increment = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) increment *= 10;

            offset = Math.clamp(offset + increment, 1, 99);
            updateOffset();

            if(event.getWhoClicked() instanceof Player player)
                player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1, 1);
        });

        defineInventoryAction(ACCEPT_SLOT, _ -> {
            if (recurring) {
                RecurringDateScheduled.create(festType, gameType, minPlayers, time, unit, offset);
            } else {
                FixedDateScheduled.create(festType, gameType, minPlayers, time);
            }

            inventory.close();
        }, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

        defineInventoryAction(CANCEL_SLOT, _ -> inventory.close());
    }

    private void updateInventory() {
        updateFestType();
        updateGameType();
        updateMinPlayerCount();
        updateMaxPlayerCount();
        updateRecurring();
        updateTime();
        updateUnit();
        updateOffset();

        updateAcceptButton();
    }

    private void updateFestType() {
        Material material = switch (festType) {
            case CASUAL -> Material.FIREWORK_ROCKET;
            case SERVER_COMPETITION -> Material.DIAMOND;
            case DUEL -> Material.DIAMOND_SWORD;
        };

        Component name = switch (festType) {
            case CASUAL -> Component.text("Casual Fest", NamedTextColor.GREEN);
            case SERVER_COMPETITION -> Component.text("Server Competition", NamedTextColor.RED);
            case DUEL -> Component.text("Duel", NamedTextColor.BLUE);
        };

        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.itemName(name);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });

        inventory.setItem(FEST_TYPE_SLOT, item);
    }

    private void updateGameType() {
        GameDefinition gameDefinition = gameType == null ? null : gameType.definition();
        ItemStack item = new ItemStack(gameDefinition == null ? Material.BARRIER : gameDefinition.iconType());

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
            meta.itemName(
                    Component.text("Minimum Players: ", NamedTextColor.GRAY)
                            .append(Component.text(minPlayers, NamedTextColor.GOLD))
            );
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Left +1", NamedTextColor.YELLOW),
                    Component.text("Right -1", NamedTextColor.YELLOW),
                    Component.text("Shift ×10", NamedTextColor.YELLOW)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setMaxStackSize(99);
        });

        item.setAmount(minPlayers);
        inventory.setItem(MIN_PLAYERS_SLOT, item);
    }

    private void updateMaxPlayerCount() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);

        item.editMeta(meta -> {
            meta.itemName(
                    Component.text("Maximum Players: ", NamedTextColor.GRAY)
                            .append(Component.text(
                                    maxPlayers == -1 ? "no limit" : String.valueOf(maxPlayers),
                                    NamedTextColor.GOLD
                            ))
            );
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Left +1", NamedTextColor.YELLOW),
                    Component.text("Right -1", NamedTextColor.YELLOW),
                    Component.text("Shift ×10", NamedTextColor.YELLOW),
                    Component.text("Below 2 = no limit", NamedTextColor.GRAY)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setMaxStackSize(99);
        });

        item.setAmount(Math.max(maxPlayers, 1));
        inventory.setItem(MAX_PLAYERS_SLOT, item);
    }

    private void updateRecurring() {
        ItemStack item = ItemStack.of(recurring
                ? Material.BELL
                : Material.WRITABLE_BOOK);

        item.editMeta(meta -> {
            meta.itemName(Component.text(
                    recurring ? "Recurring" : "One-Time",
                    NamedTextColor.GOLD
            ));
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Click to toggle", NamedTextColor.YELLOW)
            ));
        });

        inventory.setItem(RECURRING_SLOT, item);
    }

    private void updateTime() {
        ItemStack item = ItemStack.of(Material.CLOCK);

        item.editMeta(meta -> {
            meta.itemName(
                    Component.text("Time: ", NamedTextColor.GRAY)
                            .append(Component.text(
                                    TIME_FORMAT.format(time),
                                    NamedTextColor.GOLD
                            ))
            );
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Click to change", NamedTextColor.YELLOW)
            ));
        });

        inventory.setItem(TIME_SLOT, item);
    }

    private void updateUnit() {
        if (!recurring) {
            inventory.setItem(UNIT_SLOT, getFillItem().withType(Material.GRAY_STAINED_GLASS_PANE));
            return;
        }

        ItemStack item = ItemStack.of(switch (unit) {
            case MINUTES -> Material.YELLOW_GLAZED_TERRACOTTA;
            case HOURS -> Material.ORANGE_GLAZED_TERRACOTTA;
            case DAYS -> Material.RED_GLAZED_TERRACOTTA;
            case WEEKS -> Material.BROWN_GLAZED_TERRACOTTA;
            case MONTHS -> Material.GREEN_GLAZED_TERRACOTTA;
            default -> Material.GRAY_GLAZED_TERRACOTTA;
        });

        item.editMeta(meta -> {
            meta.itemName(Component.text("Unit: ", NamedTextColor.GRAY)
                    .append(Component.text(unit.toString(), NamedTextColor.GOLD))
            );
            meta.lore(ItemUtils.applyDefaultLoreStyle(Component.text("Click to change", NamedTextColor.YELLOW)));
        });

        inventory.setItem(UNIT_SLOT, item);
    }

    private void updateOffset() {
        if (!recurring) {
            inventory.setItem(OFFSET_SLOT, getFillItem().withType(Material.GRAY_STAINED_GLASS_PANE));
            return;
        }

        ItemStack item = ItemStack.of(Material.SUGAR);
        item.editMeta(meta -> {
            meta.itemName(Component.text("Offset: ", NamedTextColor.GRAY)
                    .append(Component.text(offset, NamedTextColor.GOLD))
            );
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Left +1", NamedTextColor.YELLOW),
                    Component.text("Right -1", NamedTextColor.YELLOW),
                    Component.text("Shift ×10", NamedTextColor.YELLOW)
            ));
        });
        inventory.setItem(OFFSET_SLOT, item);
    }

    private void updateAcceptButton() {
        ItemStack item = ItemStack.of(Material.LIME_DYE);
        item.editMeta(meta -> {
            meta.itemName(Component.text("Accept", NamedTextColor.GREEN));
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Click to create the fest", NamedTextColor.YELLOW)
            ));
        });

        inventory.setItem(ACCEPT_SLOT, item);
    }
}