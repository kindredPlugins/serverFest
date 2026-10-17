package at.gaderman.serverFest.gui.fests;

import at.gaderman.serverFest.config.GeneralConfig;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.definition.GameDefinition;
import at.gaderman.serverFest.fests.schedule.FixedDateScheduled;
import at.gaderman.serverFest.fests.schedule.RecurringDateScheduled;
import at.gaderman.serverFest.gui.ActionInventory;
import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.Arrays;
import java.util.List;

public class FestCreationGUI extends ActionInventory {

    private final int MAX_PLAYER_LIMIT = GeneralConfig.getInstance().maxPlayersPerFest();

    private static final FestType[] FEST_TYPES = Arrays.stream(FestType.values()).filter(FestType::isSelectectibleForScheduling).toArray(FestType[]::new);
    private static final GameType[] GAME_TYPES = GameType.values();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
    private static final List<ChronoUnit> UNITS = List.of(ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS);

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
    private int maxPlayers = GeneralConfig.getInstance().maxPlayersPerFest();

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
            if (!(event.getWhoClicked() instanceof Player player))
                return;

            int increment = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) increment *= 10;

            int value = minPlayers + increment;

            if (value > MAX_PLAYER_LIMIT || value < 1)
                player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1, 1);
            else
                player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 1, 1);

            minPlayers = Math.clamp(value, 1, MAX_PLAYER_LIMIT);

            if (minPlayers > maxPlayers) {
                maxPlayers = minPlayers;
                updateMaxPlayerCount();
            }

            updateMinPlayerCount();
        });

        defineInventoryAction(MAX_PLAYERS_SLOT, event -> {
            if (!(event.getWhoClicked() instanceof Player player))
                return;

            int increment = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) increment *= 10;

            int value = maxPlayers + increment;

            if (value > MAX_PLAYER_LIMIT || value < 2)
                player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1, 1);
            else
                player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 1, 1);

            maxPlayers = Math.clamp(value, 2, MAX_PLAYER_LIMIT);

            if(maxPlayers < minPlayers){
                minPlayers = maxPlayers;
                updateMinPlayerCount();
            }

            updateMaxPlayerCount();
        });

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
            unit = UNITS.get(Math.floorMod(UNITS.indexOf(unit) + increment, UNITS.size()));
            updateUnit();

            if (event.getWhoClicked() instanceof Player player)
                player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1, 1);
        });

        defineInventoryAction(OFFSET_SLOT, event -> {
            if (!recurring) return;

            int increment = event.isRightClick() ? -1 : 1;
            if (event.isShiftClick()) increment *= 10;

            offset = Math.clamp(offset + increment, 1, 99);
            updateOffset();

            if (event.getWhoClicked() instanceof Player player)
                player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1, 1);
        });

        defineInventoryAction(ACCEPT_SLOT, _ -> {
            if (recurring) {
                RecurringDateScheduled.create(festType, gameType, minPlayers, maxPlayers, time, unit, offset);
            } else {
                FixedDateScheduled.create(festType, gameType, minPlayers, maxPlayers, time);
            }

            inventory.close();
        }, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

        defineInventoryAction(CANCEL_SLOT, _ -> inventory.close(), Sound.ENTITY_CHICKEN_EGG, 1, 2);
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
        updateCancelButton();
    }

    private void updateFestType() {
        ItemStack item = switch (festType) {
            case CASUAL ->
                    ItemUtils.createCustomHead("http://textures.minecraft.net/texture/302f48f34d22ded7404f76e8a132af5d7919c8dcd51df6e7a85ddfac85ab");
            case SERVER_COMPETITION ->
                    ItemUtils.createCustomHead("http://textures.minecraft.net/texture/5d308ae27b58b69645497f9da86597eda947eacd10c29e3d4bbf3bc76ceb1eab");
            case DUEL -> ItemStack.of(Material.DIAMOND_SWORD);
        };

        Component name = switch (festType) {
            case CASUAL -> Component.text("Casual Fest", NamedTextColor.GREEN);
            case SERVER_COMPETITION -> Component.text("Server Competition", NamedTextColor.RED);
            case DUEL -> Component.text("Duel", NamedTextColor.BLUE);
        };

        List<Component> lore = switch (festType) {
            case CASUAL -> List.of(Component.text("Casual fest with no rewards, only fun", NamedTextColor.YELLOW));
            case SERVER_COMPETITION ->
                    List.of(Component.text("Exclusive server-wide reward, compete for it", NamedTextColor.YELLOW));
            case DUEL -> List.of(Component.text("Duel between 2 players", NamedTextColor.YELLOW));
        };

        item.editMeta(meta -> {
            meta.customName(name.decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.lore(ItemUtils.applyDefaultLoreStyle(lore));
        });

        inventory.setItem(FEST_TYPE_SLOT, item);
    }

    private void updateGameType() {
        GameDefinition gameDefinition = gameType == null ? null : gameType.definition();
        ItemStack item = gameDefinition == null
                ? ItemUtils.createCustomHead("http://textures.minecraft.net/texture/d34e063cafb467a5c8de43ec78619399f369f4a52434da8017a983cdd92516a0")
                : new ItemStack(gameDefinition.iconType());

        item.editMeta(meta -> {
            meta.displayName((gameDefinition == null
                    ? Component.text("Random Game", NamedTextColor.GOLD)
                    : gameDefinition.iconName())
                    .decoration(TextDecoration.ITALIC, false));
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

        item.setAmount(Math.clamp(minPlayers, 1, 99));
        inventory.setItem(MIN_PLAYERS_SLOT, item);
    }

    private void updateMaxPlayerCount() {
        ItemStack item = new ItemStack(Material.CREEPER_HEAD);

        item.editMeta(meta -> {
            meta.itemName(
                    Component.text("Maximum Players: ", NamedTextColor.GRAY)
                            .append(Component.text(maxPlayers, NamedTextColor.GOLD))
            );
            meta.lore(ItemUtils.applyDefaultLoreStyle(
                    Component.text("Left +1", NamedTextColor.YELLOW),
                    Component.text("Right -1", NamedTextColor.YELLOW),
                    Component.text("Shift ×10", NamedTextColor.YELLOW)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setMaxStackSize(99);
        });

        item.setAmount(Math.clamp(maxPlayers, 1, 99));
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
            case MINUTES -> Material.PAPER;
            case HOURS -> Material.BOOK;
            case DAYS -> Material.LECTERN;
            case WEEKS -> Material.CHISELED_BOOKSHELF;
            case MONTHS -> Material.BOOKSHELF;
            default -> Material.ENCHANTED_BOOK;
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

    private void updateCancelButton() {
        inventory.setItem(CANCEL_SLOT, ItemUtils.createBasicUIItem(Material.RED_DYE,
                Component.text("Cancel", NamedTextColor.RED),
                List.of(Component.text("Click to cancel", NamedTextColor.YELLOW)))
        );
    }
}