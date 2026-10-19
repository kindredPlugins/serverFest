package at.gaderman.serverFest.fests.game.definition;

import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

import java.util.List;

public class ElytraRaceDefinition implements GameDefinition {

    @Override
    public Material iconType() {
        return Material.ELYTRA;
    }

    @Override
    public Component iconName() {
        return Component.text("Elytra Race", NamedTextColor.GREEN);
    }

    @Override
    public List<Component> iconLore() {
        return List.of(
                Component.text("Fly with an elytra through a set of ")
                        .append(Component.text("hoops", NamedTextColor.YELLOW))
                        .append(Component.text(".")),
                Component.text("Each checkpoint grants a ")
                        .append(MiniMessage.miniMessage().deserialize("<sprite:\"minecraft:items\":item/firework_rocket>"))
                        .append(Component.text("firework boost", NamedTextColor.RED))
                        .append(Component.text(".")),
                Component.text("Fly through all hoops in order to win.")
        );
    }

}
