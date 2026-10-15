package at.gaderman.serverFest.fests.items;

import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Provides a sentiment of specific items which are frequently required in fests and properly caches them
 */
public class FestItemProvider {

    public static final ItemStack SPEC_COMPASS = ItemUtils.createBasicUIItem(Material.COMPASS,
            Component.text("Spectator Compass"),
            List.of(Component.text("Visit other players", NamedTextColor.YELLOW)));

}
