package at.gaderman.serverFest.fests.game.definition;

import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

import java.util.List;

public class TargetPracticeDefinition implements GameDefinition{

    @Override
    public Material iconType() {
        return Material.TARGET;
    }

    @Override
    public Component iconName() {
        return Component.text("Target Practice", NamedTextColor.RED);
    }

    @Override
    public List<Component> iconLore() {
        return List.of(
                Component.text("Shoot various ")
                        .append(Component.text("targets ", NamedTextColor.RED))
                        .append(Component.text("which appear.")),
                Component.text("Each target grants ")
                        .append(Component.text("◈ points ", NamedTextColor.GREEN))
                        .append(Component.text("based on the target distance.")),
                Component.text("Score as much as possible in the time to win.")
        );
    }

}
