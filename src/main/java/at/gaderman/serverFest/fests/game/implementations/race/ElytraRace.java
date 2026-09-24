package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.implementations.race.data.RaceGamePlayerData;
import at.gaderman.serverFest.fests.game.triggers.action.PlayerToggleGlideTrigger;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.time.StopWatch;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ElytraRace extends RaceGame implements PlayerToggleGlideTrigger, PlayerDamageTrigger {

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

    private static final double GATE_SIZE = 5;
    private static final double GATE_THICKNESS = 0.6;

    @Override
    public Location determineOrigin() {
        Location origin = super.determineOrigin();
        World world = fest.world();

        int tries = 1;
        origin.setY(100);

        while (world.getHighestBlockAt(origin).getY() > origin.getY() - 10) {
            origin = new Location(world, origin.getX() + 300, origin.getY(), 0).toCenterLocation();
            tries++;
        }

        ServerFest.getPlugin().getLogger().info("Found ElytraRace start after " + tries + " tries");

        return origin;
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        World world = fest.world();
        Location origin = fest.origin();

        for(int x = -1; x <= 1; x++)
            for(int z = -1; z <= 1; z++)
                origin.clone().add(x, -1, z).getBlock().setType(Material.BAMBOO_BLOCK);

        createElytraTrack();

        Set<ChunkPos> chunks = new HashSet<>();

        for(Vector point : trackSamples) {
            int chunkX = ((int) point.getX()) >> 4;
            int chunkZ = ((int) point.getZ()) >> 4;

            for(int x = -1; x <= 1; x++)
                for(int z = -1; z <= 1; z++)
                    chunks.add(new ChunkPos(chunkX + x, chunkZ + z));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        loadChunks(world, chunks, () -> {
            for(CheckPoint checkPoint : checkPoints)
                adjustGateLocation(checkPoint.location());

            for(int i = 1; i < checkPoints.size(); i++) {
                CheckPoint checkPoint = checkPoints.get(i);
                createGate(checkPoint.location(), GATE_SIZE, GATE_THICKNESS, i == checkPoints.size() - 1);
            }

            future.complete(null);
        });

        return future;
    }

    @Override
    protected void onStop() {
    }

    @Override
    public void kickOff() {
        Location startPoint = checkPoints.getFirst().location();

        ItemStack elytra = ItemStack.of(Material.ELYTRA);
        elytra.editMeta(meta -> meta.setUnbreakable(true));
        fest.participants().forEach(participant -> {
            participant.getEquipment().setChestplate(elytra);

            participant.setVelocity(new Vector(0, 1, 0));

            participant.getScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                participant.setGliding(true);
                participant.getWorld().spawn(participant.getLocation(), Firework.class, firework -> firework.setAttachedTo(participant));
            }, null, 5);

            WorldBorder border = Bukkit.createWorldBorder();
            border.setCenter(participant.getLocation());
            border.setSize(WAVE_AMPLITUDE * 1.2 * 2);
            participant.setWorldBorder(border);

            participant.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
                if (!fest.game().isRunning()) {
                    task.cancel();
                    return;
                }

                RaceGamePlayerData playerData = (RaceGamePlayerData) getOrCreatePlayerData(participant.getUniqueId());
                renderGuideParticles(participant, playerData.currentCheckPoint());

                if (participant.isInWater() || participant.isOnGround())
                    resetPlayerToLastCheckPoint(participant);

                border.setCenter(startPoint.x(), participant.getZ());
                participant.setWorldBorder(border);
            }, null, 4, 4);
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

    //region Players

    private void resetPlayerToLastCheckPoint(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
        player.playSound(player, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
        Particle.EXPLOSION_EMITTER.builder()
                .location(player.getLocation())
                .receivers(32)
                .spawn();

        RaceGamePlayerData playerData = (RaceGamePlayerData) getOrCreatePlayerData(player.getUniqueId());
        Location lastCheckPoint = checkPoints.get(playerData.currentCheckPoint()).location();

        player.getNearbyEntities(1, 1, 1).forEach(entity -> {
            if (entity instanceof Firework firework && firework.getAttachedTo() != null && firework.getAttachedTo().equals(player))
                firework.remove();
        });

        player.setVelocity(new Vector());
        player.teleport(lastCheckPoint.clone().add(lastCheckPoint.getDirection().clone().multiply(-2)).add(0, 1, 0));

        player.getScheduler().runDelayed(ServerFest.getPlugin(), task -> {
            if (!fest.game().isRunning()) {
                task.cancel();
                return;
            }

            player.getWorld().spawn(player.getLocation(), Firework.class, firework -> firework.setAttachedTo(player));
        }, null, 10);
    }

    //endregion

    //region Track

    // How far the track moves left/right.
    private static final double WAVE_AMPLITUDE = 100;

    private void createElytraTrack() {
        Location origin = fest.origin();

        int gateCount = 20;

        double spacing = 50.0;

        checkPoints.add(new CheckPoint(origin, GATE_SIZE, GATE_SIZE));

        // How quickly it oscillates.
        double waveLength = 2;

        double lastZ = 0;
        for (int i = 1; i < gateCount; i++) {
            double z = lastZ + spacing + Math.random() * 35;
            lastZ = z;

            // Smooth left/right movement.
            double x = Math.sin(i / waveLength) * WAVE_AMPLITUDE;

            // Optional vertical movement.
            double y = Math.random() * 20 - 10;

            Location gateLocation = origin.clone().add(x, y, z);
            checkPoints.add(new CheckPoint(gateLocation, GATE_SIZE, GATE_SIZE));
        }

        buildTrackSpline();
    }

    /**
     * Checks if gate has enough unobstructed room, shifting it upwards or clearing the space if necessary
     */
    private void adjustGateLocation(Location location) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        World world = location.getWorld();

        int minY = location.getBlockY();
        int maxY = minY + 30;

        for (int y = minY; y <= maxY; y++) {
            boolean clear = true;

            for (int x = -5; x <= 5 && clear; x++) {
                for (int z = -5; z <= 5 && clear; z++) {
                    for (int offsetY = 0; offsetY <= 5; offsetY++) {
                        if (!world.getBlockAt(location.getBlockX() + x, y + offsetY, location.getBlockZ() + z).isPassable()) {
                            clear = false;
                            break;
                        }
                    }
                }
            }

            if (clear) {
                location.setY(y == minY ? y : y + Math.random() * 10);
                stopWatch.stop();
                ServerFest.getPlugin().getLogger().info("Found a location after " + (y - minY) + " tries (" + stopWatch.getTime() + "ms)");
                return;
            }
        }

        int newY = location.getWorld().getHighestBlockAt(location).getY() + 10;
        location.setY(newY);

        location.getWorld().createExplosion(location, 6);
        stopWatch.stop();
        ServerFest.getPlugin().getLogger().info("Needed extra solution for checkpoint (" +  stopWatch.getTime() + "ms)");
    }

    //endregion

    //region Spline Border

    private final List<Vector> trackSamples = new ArrayList<>();
    private final List<Integer> segmentStartIndex = new ArrayList<>();

    private static final int SAMPLES_PER_SEGMENT = 32;

    private void buildTrackSpline() {
        trackSamples.clear();
        segmentStartIndex.clear();

        List<Vector> checkPoints = this.checkPoints.stream()
                .map(cp -> cp.location().toVector())
                .toList();

        int n = checkPoints.size();
        for (int i = 0; i < n - 1; i++) {
            segmentStartIndex.add(trackSamples.size());

            Vector p0 = checkPoints.get(Math.max(i - 1, 0));
            Vector p1 = checkPoints.get(i);
            Vector p2 = checkPoints.get(i + 1);
            Vector p3 = checkPoints.get(Math.min(i + 2, n - 1));

            for (int s = 0; s < SAMPLES_PER_SEGMENT; s++) {
                trackSamples.add(catmullRom(p0, p1, p2, p3, s / (double) SAMPLES_PER_SEGMENT));
            }
        }
        trackSamples.add(checkPoints.get(n - 1));
        segmentStartIndex.add(trackSamples.size() - 1);
    }

    private Vector catmullRom(Vector p0, Vector p1, Vector p2, Vector p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        double x = 0.5 * ((2 * p1.getX()) + (-p0.getX() + p2.getX()) * t + (2 * p0.getX() - 5 * p1.getX() + 4 * p2.getX() - p3.getX()) * t2 + (-p0.getX() + 3 * p1.getX() - 3 * p2.getX() + p3.getX()) * t3);
        double y = 0.5 * ((2 * p1.getY()) + (-p0.getY() + p2.getY()) * t + (2 * p0.getY() - 5 * p1.getY() + 4 * p2.getY() - p3.getY()) * t2 + (-p0.getY() + 3 * p1.getY() - 3 * p2.getY() + p3.getY()) * t3);
        double z = 0.5 * ((2 * p1.getZ()) + (-p0.getZ() + p2.getZ()) * t + (2 * p0.getZ() - 5 * p1.getZ() + 4 * p2.getZ() - p3.getZ()) * t2 + (-p0.getZ() + 3 * p1.getZ() - 3 * p2.getZ() + p3.getZ()) * t3);
        return new Vector(x, y, z);
    }

    private static final double TRACK_HALF_WIDTH = 6.0;
    private static final int RENDER_WINDOW = 40;

    private void renderGuideParticles(Player participant, int currentCheckPointIndex) {
        int startSample = segmentStartIndex.get(Math.min(currentCheckPointIndex, segmentStartIndex.size() - 1));
        int endSample = Math.min(startSample + RENDER_WINDOW, trackSamples.size() - 1);

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 220, 130), 1.0f);

        for (int i = startSample; i < endSample; i += 2) {
            Vector point = trackSamples.get(i);
            Vector next = trackSamples.get(Math.min(i + 1, trackSamples.size() - 1));

            Vector forward = next.clone().subtract(point);
            if (forward.lengthSquared() < 1e-6) continue;
            forward.normalize();

            Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() < 1e-6) continue;
            right.normalize();

            // sparser the further ahead — draws the eye forward instead of a flat wall of dots
            double closeness = 1.0 - ((double) (i - startSample) / RENDER_WINDOW);
            if (Math.random() > closeness * 0.8 + 0.2) continue;

            Vector left = point.clone().add(right.clone().multiply(TRACK_HALF_WIDTH));
            Vector rightSide = point.clone().subtract(right.clone().multiply(TRACK_HALF_WIDTH));

            participant.spawnParticle(Particle.DUST, left.getX(), left.getY(), left.getZ(), 1, 0, 0, 0, 0, dust);
            participant.spawnParticle(Particle.DUST, rightSide.getX(), rightSide.getY(), rightSide.getZ(), 1, 0, 0, 0, 0, dust);
        }
    }

    //endregion

    //region Event Handling


    @Override
    public void onToggleGlide(EntityToggleGlideEvent event, Player player) {
        if (!event.isGliding())
            event.setCancelled(true);
    }

    @Override
    public void onPlayerDamage(EntityDamageEvent event, Player player) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL || event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setDamage(0);
            resetPlayerToLastCheckPoint(player);
            return;
        }

        event.setCancelled(true);
    }

    //endregion
}
