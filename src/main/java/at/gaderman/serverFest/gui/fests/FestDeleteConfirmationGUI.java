package at.gaderman.serverFest.gui.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.schedule.FestCalendar;
import at.gaderman.serverFest.fests.schedule.ScheduledFest;
import at.gaderman.serverFest.gui.ActionInventory;
import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

public class FestDeleteConfirmationGUI extends ActionInventory {

    private static final int FEST_SLOT = 13;
    private static final int CONFIRM_SLOT = 20;
    private static final int CANCEL_SLOT = 24;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);

    private final ScheduledFest fest;

    public FestDeleteConfirmationGUI(ScheduledFest fest) {
        super(27, Component.text("Delete Fest?", NamedTextColor.RED));
        this.fest = fest;
        initializeInventory();
    }

    private void initializeInventory() {
        fillWithFillItem();
        inventory.setItem(FEST_SLOT, FestCalendarGUI.buildFestItem(fest, null));

        inventory.setItem(CONFIRM_SLOT, ItemUtils.createBasicUIItem(
                Material.LIME_DYE,
                Component.text("Confirm Delete", NamedTextColor.GREEN),
                List.of(Component.text("Delete this scheduled fest", NamedTextColor.YELLOW))
        ));
        inventory.setItem(CANCEL_SLOT, ItemUtils.createBasicUIItem(
                Material.RED_DYE,
                Component.text("Cancel", NamedTextColor.RED),
                List.of(Component.text("Keep this scheduled fest", NamedTextColor.YELLOW))
        ));

        defineInventoryAction(CONFIRM_SLOT, event -> {
            if (!(event.getWhoClicked() instanceof Player player))
                return;

            FestCalendar.getInstance().removeFestEntry(fest.uuid());
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            inventory.close();

            player.getScheduler().runDelayed(ServerFest.getPlugin(), _ -> new FestCalendarGUI().openInventory(player), null, 1);
        }, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        defineInventoryAction(CANCEL_SLOT, event -> {
            if (event.getWhoClicked() instanceof Player player)
                player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
            inventory.close();

            if (event.getWhoClicked() instanceof Player player)
                player.getScheduler().runDelayed(ServerFest.getPlugin(), _ -> new FestCalendarGUI().openInventory(player), null, 1);
        }, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
    }
}
