package at.gaderman.serverFest.gui.fests;

import at.gaderman.serverFest.config.PermissionProvider;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.items.FestItemProvider;
import at.gaderman.serverFest.fests.schedule.FestCalendar;
import at.gaderman.serverFest.fests.schedule.RecurringDateScheduled;
import at.gaderman.serverFest.fests.schedule.ScheduledFest;
import at.gaderman.serverFest.gui.ActionInventory;
import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

public class FestCalendarGUI extends ActionInventory {

    private static final int INVENTORY_SIZE = 54;
    private static final int NAVIGATION_ROW_SIZE = 9;
    private static final int FESTS_PER_PAGE = INVENTORY_SIZE - NAVIGATION_ROW_SIZE;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);

    private List<ScheduledFest> fests = List.of();
    private int page = 0;
    private int maxPages = 1;

    public FestCalendarGUI() {
        super(Component.text("Fest Calendar", NamedTextColor.GOLD));
    }

    @Override
    protected int calculateSize() {
        return INVENTORY_SIZE;
    }

    @Override
    public void openInventory(Player player) {
        refreshFests();
        super.openInventory(player);
        updateInventory(player);

        player.playSound(player, Sound.ENTITY_VILLAGER_WORK_LIBRARIAN, 1, 1);
    }

    private void refreshFests() {
        fests = FestCalendar.getInstance().calendar();
        maxPages = Math.max(1, (int) Math.ceil(fests.size() / (double) FESTS_PER_PAGE));
        page = Math.clamp(page, 0, maxPages - 1);
    }

    private void updateInventory(Player player) {
        page = Math.clamp(page, 0, maxPages - 1);

        inventory.clear();
        for (int i = 0; i < inventory.getSize(); i++) {
            removeInventoryAction(i);
        }

        fillEmptyWithClearFill();
        for (int i = INVENTORY_SIZE - NAVIGATION_ROW_SIZE; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, getFillItem());
        }

        int startIndex = page * FESTS_PER_PAGE;
        int endIndex = Math.min(startIndex + FESTS_PER_PAGE, fests.size());

        for (int index = startIndex; index < endIndex; index++) {
            int slot = index - startIndex;
            ScheduledFest fest = fests.get(index);

            inventory.setItem(slot, buildFestItem(fest, player));

            if (PermissionProvider.canManageFests(player)) {
                defineInventoryAction(slot, event -> {
                    if (event.isRightClick()) {
                        player.playSound(player, Sound.BLOCK_LEVER_CLICK, 1f, 1f);
                        new FestDeleteConfirmationGUI(fest).openInventory(player);
                        return;
                    }

                    if (event.isLeftClick()) {
                        player.playSound(player, Sound.BLOCK_LEVER_CLICK, 1f, 1f);
                        new FestCreationGUI(fest).openInventory(player);
                    }
                });
            }
        }

        inventory.setItem(INVENTORY_SIZE - 5, ItemUtils.createWithBasicName(Material.PAPER,
                Component.text("Page " + (page + 1) + " / " + maxPages, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        if (page > 0) {
            int prevSlot = INVENTORY_SIZE - NAVIGATION_ROW_SIZE;
            inventory.setItem(prevSlot, ItemUtils.createWithBasicName(Material.ARROW, Component.text("Previous Page")));
            defineInventoryAction(prevSlot, _ -> {
                page--;
                updateInventory(player);
            }, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        }

        if (page < maxPages - 1) {
            int nextSlot = INVENTORY_SIZE - 1;
            inventory.setItem(nextSlot, ItemUtils.createWithBasicName(Material.ARROW, Component.text("Next Page")));
            defineInventoryAction(nextSlot, _ -> {
                page++;
                updateInventory(player);
            }, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        } else if (PermissionProvider.canManageFests(player)) {
            int slot = INVENTORY_SIZE - 1;
            inventory.setItem(slot, createCreateItem());
            defineInventoryAction(slot, event -> {
                new FestCreationGUI().openInventory(player);
            });
        }
    }

    public static ItemStack buildFestItem(ScheduledFest fest, @Nullable Player player) {
        LocalDateTime nextTime = fest.nextSchedule();

        GameType gameType = fest.gameType();
        ItemStack item = gameType == null
                ? FestItemProvider.RANDOM_GAME
                : ItemStack.of(gameType.definition().iconType());
        item.editMeta(meta -> {
            meta.displayName(Component.text(fest.festType().displayName() + " Fest", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>(List.of(
                    Component.text("Next: " + DATE_FORMAT.format(nextTime), NamedTextColor.YELLOW),
                    Component.text("Schedule: " + (fest instanceof RecurringDateScheduled ? "Recurring" : "One-Time"), NamedTextColor.GRAY),
                    Component.text("Players: " + fest.minPlayers() + "-" + fest.maxPlayers(), NamedTextColor.GRAY),
                    Component.text("Game: " + (gameType == null ? "Random" : PlainTextComponentSerializer.plainText().serialize(gameType.definition().iconName())), NamedTextColor.GRAY)
            ));

            if (player != null && PermissionProvider.canManageFests(player)) {
                lore.add(Component.empty());
                lore.add(Component.text("Left click to edit", NamedTextColor.GREEN));
                lore.add(Component.text("Right click to edit", NamedTextColor.RED));
            }

            meta.lore(ItemUtils.applyDefaultLoreStyle(lore));

            if (fest.festType() == FestType.SERVER_COMPETITION)
                meta.setEnchantmentGlintOverride(true);

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    private static ItemStack createCreateItem() {
        ItemStack item = ItemUtils.createCustomHead("http://textures.minecraft.net/texture/3edd20be93520949e6ce789dc4f43efaeb28c717ee6bfcbbe02780142f716");
        item.editMeta(meta -> {
            meta.customName(Component.text("Create Fest", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(ItemUtils.applyDefaultLoreStyle(List.of(
                    Component.text("Click to create a fest", NamedTextColor.YELLOW)
            )));
        });
        return item;
    }
}
