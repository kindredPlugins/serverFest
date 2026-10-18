package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.implementations.race.arena.ElytraRaceArenaDefinition;
import at.gaderman.serverFest.fests.game.implementations.race.arena.ElytraRaceArenaLayout;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.implementations.race.data.RaceGamePlayerData;
import at.gaderman.serverFest.fests.game.triggers.action.PlayerToggleGlideTrigger;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.time.StopWatch;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ElytraRace extends RaceGame<ElytraRaceArenaLayout> implements PlayerToggleGlideTrigger, PlayerDamageTrigger {

    public ElytraRace(Fest fest, ArenaInstance arenaInstance) {
        super(fest, arenaInstance);
    }

    @Override
    public @NotNull GameType gameType() {
        return GameType.ELYTRA_RACE;
    }

    private Set<Chunk> forceLoadedChunks;

    //region Overrides

    public static final double GATE_SIZE = 5;
    public static final double GATE_THICKNESS = 0.6;

    @Override
    public Location determineOrigin() {
        Location origin = super.determineOrigin();
        origin.setY(Math.max(fest.world().getHighestBlockYAt(origin), 100));
        return origin;
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        World world = fest.world();
        Location origin = fest.origin();

        for (int x = -5; x <= 5; x++)
            for (int y = -5; y < 5; y++)
                for (int z = -5; z <= 5; z++) {
                    blockTracker.setBlock(origin.clone().add(x, y, z), Material.AIR);
                }

        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                Location location = origin.clone().add(x, -1, z);
                blockTracker.setBlock(location, Material.BAMBOO_BLOCK);
                location.getBlock().setType(Material.BAMBOO_BLOCK);
            }

        blockTracker.save();

        CompletableFuture<Void> future = new CompletableFuture<>();

        loadChunks(world, arenaLayout.chunks(), resolvedChunks -> {
            forceLoadedChunks = resolvedChunks;
            resolvedChunks.forEach(chunk -> chunk.addPluginChunkTicket(ServerFest.getPlugin()));

            checkPoints.getFirst().location().set(origin.getX(), origin.getY(), origin.getZ());

            int highestCheckPointY = Integer.MIN_VALUE;
            for (CheckPoint point : checkPoints) {
                Location checkpointLocation = point.location();
                highestCheckPointY = Math.max(highestCheckPointY, Math.max(world.getHighestBlockYAt(checkpointLocation), checkpointLocation.getBlockY()));
            }

            for (int i = 1; i < checkPoints.size(); i++)
                adjustGateLocation(checkPoints.get(i).location(), i, highestCheckPointY);

            arenaLayout.trackSamples().clear();
            arenaLayout.trackSamples().addAll(((ElytraRaceArenaDefinition) gameType().getArenaDefinition()).buildTrackSpline(arenaLayout.checkPoints()));

            checkPoints.getFirst().location().set(origin.getX(), origin.getY(), origin.getZ());

            updateArenaBuildingBar(Component.text("Generating entities..."), 0.9f);

            for (int i = 1; i < checkPoints.size(); i++) {
                CheckPoint checkPoint = checkPoints.get(i);
                createGate(checkPoint.location(), GATE_SIZE, GATE_THICKNESS, i == checkPoints.size() - 1);
            }

            updateArenaBuildingBar(Component.text("Waiting for server..."), 1f);
            future.complete(null);
        });

        return future;
    }

    @Override
    protected void onStop() {
        if (forceLoadedChunks != null)
            forceLoadedChunks.forEach(chunk -> chunk.removePluginChunkTicket(ServerFest.getPlugin()));
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
            border.setSize(ElytraRaceArenaDefinition.WAVE_AMPLITUDE * 1.2 * 2);
            participant.setWorldBorder(border);

            double originZ = fest.origin().getZ();
            double finalZ = checkPoints.getLast().location().getZ();
            participant.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
                if (!fest.game().isRunning()) {
                    task.cancel();
                    return;
                }

                RaceGamePlayerData playerData = (RaceGamePlayerData) getOrCreatePlayerData(participant.getUniqueId());
                renderGuideParticles(participant, playerData.currentCheckPoint());

                if (participant.isInWater() || participant.isOnGround())
                    resetPlayerToLastCheckPoint(participant);

                border.setCenter(startPoint.x(), Math.clamp(participant.getZ(), originZ, finalZ));
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

    /**
     * Checks if gate has enough unobstructed room, shifting it upwards or clearing the space if necessary.
     * Every checkpoint also keeps within 35 blocks vertically of the previous checkpoint and cannot sit
     * more than 50 blocks below the highest checkpoint in the track.
     */
    private void adjustGateLocation(Location location, int index, int highestCheckPointY) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        World world = location.getWorld();
        if (world == null) {
            return;
        }

        int baseY = Math.max(location.getBlockY(), highestSolidYAt(world, location));
        int previousY = index > 0 ? checkPoints.get(index - 1).location().getBlockY() : baseY;
        int sliceMinY = Math.max(highestCheckPointY - 50, previousY - 35);
        int sliceMaxY = Math.min(highestCheckPointY, previousY + 35);

        if (sliceMaxY < sliceMinY) {
            sliceMinY = highestCheckPointY - 50;
            sliceMaxY = highestCheckPointY;
        }

        List<Integer> candidateYs = new ArrayList<>();
        for (int y = sliceMinY; y <= sliceMaxY; y++) {
            candidateYs.add(y);
        }
        Collections.shuffle(candidateYs);

        for (int y : candidateYs) {
            if (hasClearGateSpace(world, location, y)) {
                location.setY(y + (Math.random() * 4.0) - 2.0);
                stopWatch.stop();
                return;
            }
        }

        for (int y = Math.max(baseY, previousY + 1); y <= highestCheckPointY + 35; y++) {
            if (hasClearGateSpace(world, location, y)) {
                location.setY(y + (Math.random() * 4.0) - 2.0);
                stopWatch.stop();
                return;
            }
        }

        for (int attempt = 1; attempt <= 5; attempt++) {
            double offset = attempt * GATE_SIZE;
            Location[] candidates = {
                    location.clone().add(offset, 0, 0),
                    location.clone().add(-offset, 0, 0),
                    location.clone().add(0, 0, offset),
                    location.clone().add(0, 0, -offset),
                    location.clone().add(offset, 0, offset),
                    location.clone().add(offset, 0, -offset),
                    location.clone().add(-offset, 0, offset),
                    location.clone().add(-offset, 0, -offset)
            };

            for (Location candidate : candidates) {
                int candidateY = Math.max(candidate.getBlockY(), highestSolidYAt(world, candidate));
                if (hasClearGateSpace(world, candidate, candidateY)) {
                    location.setX(candidate.getX());
                    location.setY(candidate.getY() + (Math.random() * 4.0) - 2.0);
                    location.setZ(candidate.getZ());
                    stopWatch.stop();
                    return;
                }
            }
        }

        location.setY(Math.max(world.getHighestBlockAt(location).getY() + 10, previousY + 15));

        for (int x = -5; x <= 5; x++)
            for (int y = -5; y < 5; y++)
                for (int z = -5; z <= 5; z++) {
                    blockTracker.setBlock(location.clone().add(x, y, z), Material.AIR);
                }
        blockTracker.save();
        stopWatch.stop();
    }

    private int highestSolidYAt(World world, Location location) {
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int highestY = Math.max(world.getMinHeight(), world.getHighestBlockYAt(location));

        for (int y = highestY; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.isPassable() && !block.isLiquid()) {
                return y;
            }
        }

        return highestY;
    }

    private boolean hasClearGateSpace(World world, Location anchor, int y) {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int offsetY = (int) (GATE_SIZE / -2); offsetY <= 5; offsetY++) {
                    Block block = world.getBlockAt(anchor.getBlockX() + x, y + offsetY, anchor.getBlockZ() + z);
                    if (!block.isPassable() || block.isLiquid()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    //endregion

    //region Spline Border

    private static final double TRACK_HALF_WIDTH = 6.0;
    private static final int RENDER_WINDOW = 40;

    private void renderGuideParticles(Player participant, int currentCheckPointIndex) {
        List<Integer> segmentStartIndex = arenaLayout.segmentStartIndex();
        List<Vector> trackSamples = arenaLayout.trackSamples();

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
