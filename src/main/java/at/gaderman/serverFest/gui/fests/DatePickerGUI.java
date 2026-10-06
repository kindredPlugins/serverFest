package at.gaderman.serverFest.gui.fests;

import at.gaderman.serverFest.gui.ActionInventory;
import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class DatePickerGUI extends ActionInventory {

    private static final int SIZE = 54;

    private static final int PREVIOUS_MONTH_SLOT = 0;
    private static final int NEXT_MONTH_SLOT = 8;

    private static final int BACK_SLOT = 45;
    private static final int QUICK_NOW_SLOT = 49;
    private static final int QUICK_NOON_SLOT = 51;
    private static final int CONFIRM_SLOT = 53;

    private static final int HOUR_DISPLAY_SLOT = 21;
    private static final int TIME_DISPLAY_SLOT = 22;
    private static final int MINUTE_DISPLAY_SLOT = 23;

    private enum View {
        CALENDAR,
        TIME
    }

    private LocalDateTime dateTime;
    private final Player player;
    private final ActionInventory prevInv;
    private final Consumer<LocalDateTime> onDatePicked;

    private YearMonth displayedMonth;
    private View view = View.CALENDAR;

    public DatePickerGUI(LocalDateTime dateTime, Player player, ActionInventory prevInv, Consumer<LocalDateTime> onDatePicked) {
        super();

        this.dateTime = dateTime.withSecond(0).withNano(0);
        this.player = player;
        this.prevInv = prevInv;
        this.onDatePicked = onDatePicked;

        this.displayedMonth = YearMonth.from(this.dateTime);
    }

    @Override
    protected void initializeInventory(int size, Component inventoryName) {
        super.initializeInventory(SIZE, inventoryName);

        fillWithFillItem();

        for (int slot = 0; slot < SIZE; slot++) {
            int finalSlot = slot;

            defineInventoryAction(finalSlot, event -> handleClick(finalSlot, event),
                    Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }

    @Override
    public void openInventory(Player player) {
        super.openInventory(player);
        updateInventory();
    }

    private void handleClick(int slot, InventoryClickEvent event) {
        switch (view) {
            case CALENDAR -> handleCalendarClick(slot);
            case TIME -> handleTimeClick(slot, event);
        }
    }

    private void updateInventory() {
        fillWithFillItem();

        switch (view) {
            case CALENDAR -> updateCalendar();
            case TIME -> updateTime();
        }
    }

    private void updateCalendar() {
        ItemStack previous = createItem(
                Material.ARROW,
                Component.text(
                        "← " + displayedMonth.minusMonths(1)
                                .getMonth()
                                .getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        NamedTextColor.WHITE
                )
        );

        ItemStack next = createItem(
                Material.ARROW,
                Component.text(
                        displayedMonth.plusMonths(1)
                                .getMonth()
                                .getDisplayName(TextStyle.FULL, Locale.getDefault())
                                + " →",
                        NamedTextColor.WHITE
                )
        );

        inventory.setItem(PREVIOUS_MONTH_SLOT, previous);
        inventory.setItem(NEXT_MONTH_SLOT, next);

        LocalDate firstDay = displayedMonth.atDay(1);

        // Monday = 0 ... Sunday = 6
        int firstDayOffset = firstDay.getDayOfWeek().getValue() - 1;

        for (int day = 1; day <= displayedMonth.lengthOfMonth(); day++) {
            int index = firstDayOffset + day - 1;

            int row = index / 7;
            int column = index % 7 + 1;

            int slot = row * 9 + column;

            LocalDate date = displayedMonth.atDay(day);

            boolean selected = date.equals(dateTime.toLocalDate());
            boolean today = date.equals(LocalDate.now());

            Material material = selected
                    ? Material.YELLOW_CONCRETE
                    : today
                    ? Material.CLOCK
                    : Material.PAPER;

            NamedTextColor color = selected
                    ? NamedTextColor.YELLOW
                    : today
                    ? NamedTextColor.GREEN
                    : NamedTextColor.WHITE;

            inventory.setItem(slot, createItem(material,
                    Component.text(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            + " " + day, color),
                    Component.text(date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), NamedTextColor.GRAY)));
        }

        inventory.setItem(BACK_SLOT, createItem(Material.BARRIER,
                Component.text("Back", NamedTextColor.RED)
        ));

        inventory.setItem(QUICK_NOW_SLOT, createItem(Material.BEACON,
                Component.text("Today", NamedTextColor.GREEN)
        ));

        inventory.setItem(CONFIRM_SLOT, createItem(Material.ARROW,
                Component.text(dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), NamedTextColor.YELLOW)
        ));
    }

    private void handleCalendarClick(int slot) {
        if (slot == PREVIOUS_MONTH_SLOT) {
            displayedMonth = displayedMonth.minusMonths(1);
            updateInventory();
            return;
        }

        if (slot == NEXT_MONTH_SLOT) {
            displayedMonth = displayedMonth.plusMonths(1);
            updateInventory();
            return;
        }

        if (slot == BACK_SLOT) {
            goBack();
            return;
        }

        if (slot == QUICK_NOW_SLOT) {
            LocalDate now = LocalDate.now();

            dateTime = LocalDateTime.of(now, dateTime.toLocalTime().withSecond(0).withNano(0));
            displayedMonth = YearMonth.from(now);

            updateInventory();
            return;
        }

        if (slot == CONFIRM_SLOT) {
            view = View.TIME;
            updateInventory();
            return;
        }

        int row = slot / 9;
        int column = slot % 9;

        if (column < 1 || column > 7 || row > 5) {
            return;
        }

        int firstDayOffset = displayedMonth.atDay(1).getDayOfWeek().getValue() - 1;

        int day = row * 7 + column - firstDayOffset;

        if (day < 1 || day > displayedMonth.lengthOfMonth()) {
            return;
        }

        LocalDate selectedDate = displayedMonth.atDay(day);
        dateTime = LocalDateTime.of(selectedDate, dateTime.toLocalTime().withSecond(0).withNano(0));

        view = View.TIME;
        updateInventory();
    }

    private void updateTime() {
        inventory.setItem(4, createItem(Material.WRITABLE_BOOK,
                Component.text(dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), NamedTextColor.YELLOW)
        ));

        // Hour control
        ItemStack hour = new ItemStack(Material.CLOCK, Math.max(1, dateTime.getHour()));

        ItemMeta hourMeta = hour.getItemMeta();
        hourMeta.displayName(Component.text(String.format("%02d", dateTime.getHour()), NamedTextColor.WHITE));
        hourMeta.lore(List.of(
                Component.text("Left click  +1", NamedTextColor.GRAY),
                Component.text("Right click  -1", NamedTextColor.GRAY),
                Component.text("Shift click  ±10", NamedTextColor.DARK_GRAY)
        ));
        hour.setItemMeta(hourMeta);

        inventory.setItem(HOUR_DISPLAY_SLOT, hour);

        inventory.setItem(TIME_DISPLAY_SLOT, createItem(
                Material.BELL,
                Component.text(String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute()), NamedTextColor.YELLOW)
        ));

        ItemStack minute = new ItemStack(Material.CLOCK, Math.max(1, dateTime.getMinute()));
        ItemMeta minuteMeta = minute.getItemMeta();
        minuteMeta.displayName(Component.text(String.format("%02d", dateTime.getMinute()), NamedTextColor.WHITE));
        minuteMeta.lore(List.of(
                Component.text("Left click  +1", NamedTextColor.GRAY),
                Component.text("Right click  -1", NamedTextColor.GRAY),
                Component.text("Shift click  ±10", NamedTextColor.DARK_GRAY)
        ));
        minute.setItemMeta(minuteMeta);

        inventory.setItem(MINUTE_DISPLAY_SLOT, minute);

        inventory.setItem(BACK_SLOT, createItem(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW)
        ));

        inventory.setItem(QUICK_NOW_SLOT, createItem(
                Material.CLOCK,
                Component.text("Now", NamedTextColor.GREEN)
        ));

        inventory.setItem(QUICK_NOON_SLOT, createItem(
                Material.SUNFLOWER,
                Component.text("Noon", NamedTextColor.YELLOW)
        ));

        inventory.setItem(CONFIRM_SLOT, createItem(Material.LIME_DYE,
                Component.text("Confirm", NamedTextColor.GREEN),
                Component.text(
                        dateTime.format(
                                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                        ), NamedTextColor.GRAY
                )
        ));
    }

    private void handleTimeClick(int slot, InventoryClickEvent event) {
        if (slot == BACK_SLOT) {
            view = View.CALENDAR;
            displayedMonth = YearMonth.from(dateTime);
            updateInventory();
            return;
        }

        if (slot == QUICK_NOW_SLOT) {
            LocalDateTime now = LocalDateTime.now()
                    .withSecond(0)
                    .withNano(0);

            dateTime = LocalDateTime.of(
                    dateTime.toLocalDate(),
                    now.toLocalTime()
            );

            updateInventory();
            return;
        }

        if (slot == QUICK_NOON_SLOT) {
            dateTime = dateTime
                    .withHour(12)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);

            updateInventory();
            return;
        }

        if (slot == CONFIRM_SLOT) {
            confirm();
            return;
        }

        if (slot == HOUR_DISPLAY_SLOT)
            changeHour(getAmount(event) * (event.isLeftClick() ? 1 : -1));

        if (slot == MINUTE_DISPLAY_SLOT)
            changeMinute(getAmount(event) * (event.isLeftClick() ? 1 : -1));
    }

    private int getAmount(InventoryClickEvent event) {
        return event.isShiftClick() ? 10 : 1;
    }

    private void changeHour(int amount) {
        dateTime = dateTime.plusHours(amount)
                .withSecond(0)
                .withNano(0);

        updateInventory();
    }

    private void changeMinute(int amount) {
        dateTime = dateTime.plusMinutes(amount)
                .withSecond(0)
                .withNano(0);

        updateInventory();
    }

    private void goBack() {
        prevInv.openInventory(player);
    }

    private void confirm() {
        dateTime = dateTime
                .withSecond(0)
                .withNano(0);

        inventory.close();
        prevInv.openInventory(player);
        onDatePicked.accept(dateTime);
    }

    private ItemStack createItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);

        item.editMeta(meta -> {
            meta.itemName(name);

            if (lore.length > 0)
                meta.lore(ItemUtils.applyDefaultLoreStyle(lore));
        });
        return item;
    }
}
