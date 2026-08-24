package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.FestGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

public class MountRace extends FestGame {
    public MountRace(Fest fest) {
        super(fest);
    }

    @Override
    public void setUpWorld() {

    }

    @Override
    public void onStart() {

    }

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
        return List.of();
    }
}
