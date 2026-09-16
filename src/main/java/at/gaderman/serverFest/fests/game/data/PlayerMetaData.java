package at.gaderman.serverFest.fests.game.data;

import at.gaderman.serverFest.ServerFest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Used to save metaData of a player like health, used for saving such data when switching to a fest
 * Allows serialization to an item and deserializing it and applying it to a player
 */
public class PlayerMetaData {

    private static final List<MetaData<?>> META_DATA = List.of(
            new MetaData<>("health", PersistentDataType.DOUBLE, 20.0, Player::getHealth, Player::setHealth),
            new MetaData<>("hunger", PersistentDataType.INTEGER, 20, Player::getFoodLevel, Player::setFoodLevel),
            new MetaData<>("saturation", PersistentDataType.FLOAT, 5.0f, Player::getSaturation, Player::setSaturation),
            new MetaData<>("exhaustion", PersistentDataType.FLOAT, 0.0f, Player::getExhaustion, Player::setExhaustion),
            new MetaData<>("level", PersistentDataType.INTEGER, 0, Player::getLevel, Player::setLevel),
            new MetaData<>("xp", PersistentDataType.FLOAT, 0.0f, Player::getExp, Player::setExp),
            new MetaData<>("fall_distance", PersistentDataType.FLOAT, 0.0f, Player::getFallDistance, Player::setFallDistance)
    );

    public static ItemStack createPlayerDataItem(Player player) {
        ItemStack item = ItemStack.of(Material.TOTEM_OF_UNDYING);

        item.editMeta(meta -> {
            meta.itemName(Component.text("Player Data", NamedTextColor.YELLOW));

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            for (MetaData<?> data : META_DATA)
                data.save(player, pdc);

            meta.lore(
                    META_DATA.stream()
                            .map(metaData -> Component.text("> ", NamedTextColor.GRAY)
                                    .append(Component.text("" + metaData.obtainValue.apply(player), NamedTextColor.BLUE))
                                    .decoration(TextDecoration.ITALIC, false))
                            .toList()
            );
        });

        return item;
    }

    public static void applyToPlayer(Player player, ItemStack item) {
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        for (MetaData<?> data : META_DATA)
            data.apply(player, pdc);
    }

    public static void resetPlayer(Player player) {
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setGliding(false);

        for (MetaData<?> data : META_DATA)
            data.reset(player);
    }

    private record MetaData<T>(NamespacedKey key, PersistentDataType<?, T> type, T defaultValue,
                               Function<Player, T> obtainValue, BiConsumer<Player, T> applyValue) {

        private MetaData(String key, PersistentDataType<?, T> type, T defaultValue, Function<Player, T> obtainValue, BiConsumer<Player, T> applyValue) {
            this(new NamespacedKey(ServerFest.getPlugin(), key), type, defaultValue, obtainValue, applyValue);
        }

        private void save(Player player, PersistentDataContainer pdc) {
            pdc.set(key, type, obtainValue.apply(player));
        }

        private void apply(Player player, PersistentDataContainer pdc) {
            applyValue.accept(player, pdc.getOrDefault(key, type, defaultValue));
        }

        private void reset(Player player) {
            applyValue.accept(player, defaultValue);
        }
    }
}
