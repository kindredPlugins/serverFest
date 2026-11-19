package at.gaderman.serverFest.fests.game.definition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

public class JumpAndRunDefinition implements GameDefinition {

    @Override
    public Material iconType() {
        return Material.DIAMOND_BOOTS;
    }

    @Override
    public Component iconName() {
        return Component.text("Jump and Run", NamedTextColor.AQUA);
    }

    @Override
    public List<Component> iconLore() {
        return List.of(
                Component.text("Race through a set of jumps and reach all ")
                        .append(Component.text("checkpoints", NamedTextColor.GOLD))
                        .append(Component.text(".")),
                Component.text("The first to reach the final point wins.")
        );
    }

}
