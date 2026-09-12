package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.List;

public class ElytraRace extends RaceGame {
    public ElytraRace(Fest fest) {
        super(fest);
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

    //region Overrides

    @Override
    public void setUpWorld() {
        World world = fest.world();

        Location spawnPoint = new Location(world, fest.offset(), 125, 0).toCenterLocation();
        while (world.getHighestBlockAt(spawnPoint).getY() > spawnPoint.getY() - 20) {
            spawnPoint = new Location(world, spawnPoint.getX() + 300, spawnPoint.getY(), 0).toCenterLocation();
        }

        world.setSpawnLocation(spawnPoint.toCenterLocation());

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                spawnPoint.clone().add(x, -1, z).getBlock().setType(Material.BAMBOO_BLOCK);
            }
        }

        createElytraTrack();
    }

    @Override
    protected void onStop() {}

    @Override
    public void kickOff() {
        fest.participants().forEach(participant -> {
            participant.getEquipment().setChestplate(ItemStack.of(Material.ELYTRA));

            participant.setJumping(true);
            participant.setVelocity(new Vector(0, 1, 1));
            participant.setGliding(true);
        });
    }

    @Override
    protected void onPassCheckPoint(Player player, int checkPoint) {
        Firework firework = player.launchProjectile(Firework.class);
        firework.setAttachedTo(player);
        player.sendMessage(Component.text("Passed checkpoint ", NamedTextColor.GRAY)
                .append(Component.text(checkPoint, NamedTextColor.GOLD)));
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    //endregion

    //region Track

    private void createElytraTrack() {
        World world = fest.world();

        Location origin = world.getSpawnLocation();

        int gateCount = 20;

        double spacing = 50.0;
        double gateSize = 5.0;
        double thickness = 0.6;

        checkPoints.add(new CheckPoint(origin, gateSize, gateSize));

        // How far the track moves left/right.
        double waveAmplitude = 100;

        // How quickly it oscillates.
        double waveLength = 2;

        double lastZ = 0;
        for (int i = 1; i < gateCount; i++) {
            double z = lastZ + spacing + Math.random() * 35;
            lastZ = z;

            // Smooth left/right movement.
            double x = Math.sin(i / waveLength) * waveAmplitude;

            // Optional vertical movement.
            double y = Math.random() * 20 - 10;

            Location gateLocation = origin.clone().add(x, y, z);
            checkPoints.add(new CheckPoint(gateLocation, gateSize, gateSize));

            createGate(gateLocation, gateSize, thickness, i == gateCount - 1);
        }
    }

    private void createGate(Location center, double size, double thickness, boolean finish) {
        double half = size / 2.0;

        // Bottom-left corner of the entire gate.
        Location origin = center.clone().add(-half, -half, 0);

        // Bottom
        spawnGlass(origin.clone(), size, thickness, thickness, finish);

        // Top
        spawnGlass(origin.clone().add(0, size - thickness, 0), size, thickness, thickness, finish);

        // Left
        spawnGlass(origin.clone(), thickness, size, thickness, finish);

        // Right
        spawnGlass(origin.clone().add(size - thickness, 0, 0), thickness, size, thickness, finish);
    }

    private void spawnGlass(Location location, double x, double y, double z, boolean finish) {
        World world = location.getWorld();
        world.getChunkAtAsync(location).thenAccept(_ -> {
            world.spawn(location, BlockDisplay.class, display -> {

                display.setBlock((finish ? Material.DIAMOND_BLOCK : Material.YELLOW_STAINED_GLASS)
                        .createBlockData());
                Transformation transformation = display.getTransformation();
                transformation.getScale().set((float) x, (float) y, (float) z);
                display.setTransformation(transformation);
            });
        });
    }

    //endregion
}
