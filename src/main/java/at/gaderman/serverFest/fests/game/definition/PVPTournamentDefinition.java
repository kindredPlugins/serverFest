package at.gaderman.serverFest.fests.game.definition;

import at.gaderman.serverFest.utils.ItemUtils;
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
                Component.text("Repeatedly engage in ")
                .append(Component.text("1v1 ", NamedTextColor.BLUE))
                .append(Component.text("pvp duels.")),
                Component.text("Win to continue, lose and you are out."),
                Component.text("Win the final battle to become the real winner.")
        );
    }

}
