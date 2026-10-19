package at.gaderman.serverFest.fests.game.definition;

import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

import java.util.List;

public class MountRaceDefinition implements GameDefinition{

    @Override
    public Material iconType() {
        return Material.SADDLE;
    }

    @Override
    public Component iconName() {
        return Component.text("Mount Race", NamedTextColor.GOLD);
    }

    @Override
    public List<Component> iconLore() {
        return List.of(
                Component.text("Ride along a track on a horse passing through ")
                        .append(Component.text("hoops", NamedTextColor.YELLOW))
                        .append(Component.text(".")),
                Component.text("Ride through all hoops in order to win.")
        );
    }

}
