package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.implementations.race.data.RaceGamePlayerData;
import at.gaderman.serverFest.fests.game.triggers.action.PlayerToggleGlideTrigger;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

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

                if(participant.isInWater())
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

        player.setGliding(true);

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
        World world = fest.world();

        Location origin = world.getSpawnLocation();

        int gateCount = 20;

        double spacing = 50.0;
        double gateSize = 5.0;
        double thickness = 0.6;

        checkPoints.add(new CheckPoint(origin, gateSize, gateSize));

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

            //TODO: check if gate is not obstructed, needs to be accessible from all sides, maybe explosions could help?

            Location gateLocation = origin.clone().add(x, y, z);
            checkPoints.add(new CheckPoint(gateLocation, gateSize, gateSize));

            createGate(gateLocation, gateSize, thickness, i == gateCount - 1);
        }

        buildTrackSpline();
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
        if(!event.isGliding())
            resetPlayerToLastCheckPoint(player);
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
