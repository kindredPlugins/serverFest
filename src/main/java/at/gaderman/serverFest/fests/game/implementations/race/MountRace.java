package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MountRace extends RaceGame implements PlayerDamageTrigger {
    public MountRace(Fest fest) {
        super(fest);
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

    @Override
    public void setUpWorld() {
        World world = fest.world();

        Location spawnPoint = new Location(world, fest.offset(), world.getSeaLevel(), 0).toCenterLocation();
        spawnPoint.setY(world.getHighestBlockAt(spawnPoint).getY() + 1);

        world.setSpawnLocation(spawnPoint);

        buildTrack();
    }

    @Override
    protected void onStop() {
    }

    @Override
    protected void kickOff() {
        fest.participants().forEach(player -> {
            player.getWorld().spawn(player.getLocation(), Horse.class, horse -> {
                horse.setAdult();

                horse.setTamed(true);
                horse.getEquipment().setItem(EquipmentSlot.SADDLE, ItemStack.of(Material.SADDLE));
                horse.addPassenger(player);

                horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3f);
                horse.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0.7f);
            });
        });
    }

    @Override
    protected void onPassCheckPoint(Player player, int checkPoint) {
        player.sendMessage(Component.text("Passed checkpoint ", NamedTextColor.GRAY)
                .append(Component.text(checkPoint, NamedTextColor.GOLD)));
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    //region Track

    private void buildTrack() {
        World world = fest.world();
        Location origin = world.getSpawnLocation().clone().add(0, -1, 0);

        int trackDiameter = 2;
        int checkPointCount = 10;
        double gateSize = trackDiameter * 2 + 1;

        Location lastLocation = origin;
        for (int i = 0; i < checkPointCount; i++) {
            double newX = lastLocation.getX() + (Math.random() - 0.5) * 30;
            double newZ = lastLocation.getZ() + Math.random() * 15 + 30;

            Location newLocation = new Location(world, newX, origin.getY(), newZ);
            checkPoints.add(new CheckPoint(newLocation, gateSize, gateSize));

            List<BlockPos> path = createPath(lastLocation, newLocation, trackDiameter);
            Set<ChunkPos> chunks = getRequiredChunks(path);

            int finalI = i;
            loadChunks(world, chunks, () -> {
                path.forEach(pos -> {
                    Location location = new Location(world, pos.x, pos.y, pos.z);

                    Block block = location.getBlock();
                    if(block.isLiquid())
                        location.add(0, 1, 0);

                    for (int y = 0; y < 5; y++) {
                        location.clone().add(0, y + 1, 0).getBlock().setType(Material.AIR);
                    }

                    block = location.getBlock();
                    block.setType(Material.SPRUCE_SLAB);
                });

                if (finalI != 0)
                    createGate(newLocation.clone().add(0, gateSize / 2 * 0.8, 0), gateSize, 0.6, finalI == checkPointCount - 1);
            });

            lastLocation = newLocation;
        }

    }

    //TODO: stationary y-axis path is boring, we either need y variation or just adjust to terrain and give players more options to make it feel more like a race
    private List<BlockPos> createPath(Location from, Location to, int trackDiameter) {
        List<BlockPos> blocks = new ArrayList<>();

        double minX = Math.min(from.getX(), to.getX()) - trackDiameter;
        double maxX = Math.max(from.getX(), to.getX()) + trackDiameter;
        double minZ = Math.min(from.getZ(), to.getZ()) - trackDiameter;
        double maxZ = Math.max(from.getZ(), to.getZ()) + trackDiameter;

        int startX = Location.locToBlock(minX);
        int endX = Location.locToBlock(maxX);
        int startZ = Location.locToBlock(minZ);
        int endZ = Location.locToBlock(maxZ);

        Vector start = from.toVector();
        Vector end = to.toVector();
        Vector line = end.clone().subtract(start);
        double lengthSquared = line.lengthSquared();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                Vector point = new Vector(x + 0.5, from.getY(), z + 0.5);
                Vector relative = point.clone().subtract(start);

                double t = Math.max(0, Math.min(1, relative.dot(line) / lengthSquared));
                Vector closest = start.clone().add(line.clone().multiply(t));

                double distance = point.clone().subtract(closest).setY(0).length();

                if (distance <= trackDiameter)
                    blocks.add(new BlockPos(x, Location.locToBlock(from.getY()), z));
            }
        }

        return blocks;
    }

    private Set<ChunkPos> getRequiredChunks(List<BlockPos> blocks) {
        return blocks.stream()
                .map(block -> new ChunkPos(block.x() >> 4, block.z() >> 4))
                .collect(Collectors.toSet());
    }

    private void loadChunks(World world, Set<ChunkPos> chunks, Runnable finished) {
        if (chunks.isEmpty()) {
            finished.run();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(chunks.size());

        for (ChunkPos chunk : chunks) {
            world.getChunkAtAsync(chunk.x(), chunk.z(), true, loaded -> {
                if (remaining.decrementAndGet() == 0)
                    finished.run();
            });
        }
    }

    //endregion

    //region Event Handling

    @Override
    public void onPlayerDamage(EntityDamageEvent event, Player player) {
        event.setCancelled(true);
    }


    //endregion

    private record BlockPos(int x, int y, int z) {
    }

    private record ChunkPos(int x, int z) {
    }
}
