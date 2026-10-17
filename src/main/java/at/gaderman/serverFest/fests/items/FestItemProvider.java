package at.gaderman.serverFest.fests.items;

import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    public static final ItemStack RANDOM_GAME = createRandomGame();

    private static ItemStack createRandomGame() {
        ItemStack item = ItemUtils.createCustomHead("http://textures.minecraft.net/texture/d34e063cafb467a5c8de43ec78619399f369f4a52434da8017a983cdd92516a0");
        item.editMeta(meta -> meta.customName(Component.text("Random Game", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        return item;
    }
}
