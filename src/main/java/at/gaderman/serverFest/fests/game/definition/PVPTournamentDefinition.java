package at.gaderman.serverFest.fests.game.definition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

public class PVPTournamentDefinition implements GameDefinition{

    @Override
    public Material iconType() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    public Component iconName() {
        return Component.text("PVP Tournament", NamedTextColor.BLUE);
    }

    @Override
    public List<Component> iconLore() {
        return List.of(
                Component.text("Compete in a single-elimination bracket split"),
                Component.text("into multiple PVP 1v1s against others")
        );
    }

}
