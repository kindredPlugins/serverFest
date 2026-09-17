package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Nautilus;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
            spawnHorseFor(player);

            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
        });
    }

    @Override
    protected void onPassCheckPoint(Player player, int checkPoint) {
        player.sendMessage(Component.text("Passed checkpoint ", NamedTextColor.GRAY)
                .append(Component.text(checkPoint, NamedTextColor.GOLD)));
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    //region Player

    private void scheduleMountSwitcher(Player player, Vehicle vehicle) {
        boolean isHorse = vehicle instanceof Horse;
        vehicle.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            if(!fest.game().isRunning() || !player.isValid()) {
                task.cancel();
                return;
            }

            if(isHorse && vehicle.isInWater()){
                player.spawnParticle(Particle.WHITE_SMOKE, vehicle.getLocation(), 50);
                player.playSound(vehicle.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 2f);

                vehicle.remove();
                spawnNautilusFor(player);
            }

            if(!isHorse && vehicle.isOnGround() && !vehicle.isInWater()) {
                player.spawnParticle(Particle.WHITE_SMOKE, vehicle.getLocation(), 50);
                player.playSound(vehicle.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 2f);

                vehicle.remove();
                spawnHorseFor(player);
            }
        }, null, 1, 1);
    }

    private void spawnHorseFor(Player player){
        player.getWorld().spawn(player.getLocation(), Horse.class, horse -> {
            horse.setAdult();

            horse.setTamed(true);
            horse.getEquipment().setItem(EquipmentSlot.SADDLE, ItemStack.of(Material.SADDLE));
            horse.addPassenger(player);

            horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3f);
            horse.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0.7f);

            scheduleMountSwitcher(player, horse);
        });
    }

    private void spawnNautilusFor(Player player){
        player.getWorld().spawn(player.getLocation(), Nautilus.class, nautilus -> {
            nautilus.setAdult();

            nautilus.setTamed(true);
            nautilus.getEquipment().setItem(EquipmentSlot.SADDLE, ItemStack.of(Material.SADDLE));
            nautilus.addPassenger(player);

            scheduleMountSwitcher(player, nautilus);
        });
    }

    //endregion

    //region Track

    private void buildTrack() {
        World world = fest.world();
        Location origin = world.getSpawnLocation().clone().add(0, -1, 0);

        int trackDiameter = 2;
        int checkPointCount = 10;
        double gateSize = trackDiameter * 2 + 1;

        buildTrackSection(world, origin, trackDiameter, gateSize, checkPointCount, 0);
    }

    //TODO: y generation is too considerate, since each pos works on its own there are especially big disparities for things like trees
    private void buildTrackSection(World world, Location lastLocation, int trackDiameter,
                                   double gateSize, int checkPointCount, int index) {
        if (index >= checkPointCount)
            return;

        double newX = lastLocation.getX() + (Math.random() - 0.5) * 45;
        double newZ = lastLocation.getZ() + Math.random() * 15 + 30;

        Location newLocation = new Location(world, newX, lastLocation.getY(), newZ);

        List<BlockPos> initialPath = createPath(lastLocation, newLocation, trackDiameter);
        Set<ChunkPos> chunks = getRequiredChunks(initialPath);

        loadChunks(world, chunks, () -> {
            int highestY = world.getHighestBlockYAt(newLocation);
            newLocation.setY(highestY + 1);

            while ((newLocation.getBlock().isPassable() && !newLocation.getBlock().isLiquid()) || newLocation.getBlock().getType().toString().contains("LEAVES"))
                newLocation.setY(newLocation.getY() - 1);

            checkPoints.add(new CheckPoint(newLocation, gateSize, gateSize));

            createPath(lastLocation, newLocation, trackDiameter).forEach(pos -> {
                Location location = new Location(world, pos.x, pos.y, pos.z);
                int maxY = world.getHighestBlockYAt(location);
                if (location.getY() > maxY)
                    location.setY(maxY);

                while (location.getBlock().isPassable() && !location.getBlock().isLiquid()) {
                    location.setY(location.getY() - 1);
                }

                Block block = location.getBlock();
                if (block.isLiquid()) {
                    boolean isDeepWater = true;
                    for(int y = 0; y < 5; y++){
                        if(location.clone().add(0, -y - 1, 0).getBlock().getType() != Material.WATER) {
                            isDeepWater = false;
                            break;
                        }
                    }

                    if(isDeepWater){
                        return;
                    }
                    else location.add(0, 1, 0);
                }

                for (int y = 0; y < 25; y++)
                    location.clone().add(0, y + 1, 0).getBlock().setType(Material.AIR);

                location.getBlock().setType(Material.SPRUCE_SLAB);
            });

            if (index != 0)
                createGate(newLocation.clone().add(0, gateSize / 2 * 0.8, 0),
                        gateSize, 0.6, index == checkPointCount - 1);

            buildTrackSection(world, newLocation, trackDiameter, gateSize, checkPointCount, index + 1);
        });
    }

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

        Vector startXZ = start.clone().setY(0);
        Vector lineXZ = line.clone().setY(0);
        double lengthSquared = lineXZ.lengthSquared();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                Vector point = new Vector(x + 0.5, 0, z + 0.5);
                Vector relative = point.clone().subtract(startXZ);

                double t = Math.max(0, Math.min(1, relative.dot(lineXZ) / lengthSquared));
                Vector closest = startXZ.clone().add(lineXZ.clone().multiply(t));

                double distance = point.distance(closest);

                if (distance <= trackDiameter) {
                    int y = Location.locToBlock(from.getY() + (to.getY() - from.getY()) * t);
                    blocks.add(new BlockPos(x, y, z));
                }
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
