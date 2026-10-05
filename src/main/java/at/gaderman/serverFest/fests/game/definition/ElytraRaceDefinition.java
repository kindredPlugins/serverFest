package at.gaderman.serverFest.fests.game.definition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

public class ElytraRaceDefinition implements GameDefinition{

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
        return List.of();
    }

}
