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
    private List<Block> placedBlocks = new ArrayList<>();

    //region Overrides

    public static final double GATE_SIZE = 5;
    public static final double GATE_THICKNESS = 0.6;

    @Override
    public Location determineOrigin() {
        Location origin = super.determineOrigin();
        World world = fest.world();

        int tries = 1;
        origin.setY(100);

//        while (world.getHighestBlockAt(origin).getY() > origin.getY() - 10) {
//            origin.set(origin.getX() + 300, origin.getY(), origin.getZ());
//            tries++;
//        }
//
//        ServerFest.getPlugin().getLogger().info("Found ElytraRace start after " + tries + " tries");

        return origin;
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        World world = fest.world();
        Location origin = fest.origin();

        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                Location location = origin.clone().add(x, -1, z);
                location.getBlock().setType(Material.BAMBOO_BLOCK);
                placedBlocks.add(location.getBlock());
            }

        CompletableFuture<Void> future = new CompletableFuture<>();

        loadChunks(world, arenaLayout.chunks(), resolvedChunks -> {
            forceLoadedChunks = resolvedChunks;
            resolvedChunks.forEach(chunk -> chunk.addPluginChunkTicket(ServerFest.getPlugin()));

            for (CheckPoint checkPoint : checkPoints)
                adjustGateLocation(checkPoint.location());

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
    protected CompletableFuture<Void> onReset() {
        return super.onReset().thenRun(() -> placedBlocks.forEach(block -> block.setType(Material.AIR)));
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
                        Block block = world.getBlockAt(location.getBlockX() + x, y + offsetY, location.getBlockZ() + z);
                        if (!block.isPassable() || block.isLiquid()) {
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
        ServerFest.getPlugin().getLogger().info("Needed extra solution for checkpoint (" + stopWatch.getTime() + "ms)");
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
