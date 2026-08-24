package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.FestGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

public class ElytraRace extends FestGame {
    public ElytraRace(Fest fest) {
        super(fest);
    }

    @Override
    public void setUpWorld() {
        World world = fest.world();

        Location spawnPoint = new Location(world, fest.offset(), 125, 0);
        while(world.getHighestBlockAt(spawnPoint).getY() > spawnPoint.getY() - 20) {
            spawnPoint = new Location(world, spawnPoint.getX() + 300, spawnPoint.getY(), 0);
        }

        world.setSpawnLocation(spawnPoint.toCenterLocation());

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                spawnPoint.clone().add(x, -1, z).getBlock().setType(Material.BAMBOO_BLOCK);
            }
        }
    }

    @Override
    public void onStart() {
        fest.participants().forEach(participant -> {
            participant.getEquipment().setChestplate(ItemStack.of(Material.ELYTRA));

            participant.setVelocity(new Vector(0, 1, 0));
        });
    }

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
