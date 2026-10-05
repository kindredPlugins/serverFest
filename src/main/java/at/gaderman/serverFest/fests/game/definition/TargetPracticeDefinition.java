package at.gaderman.serverFest.fests.game.definition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        return List.of();
    }

}
