package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.implementations.race.arena.RaceGameArenaLayout;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.implementations.race.data.RaceGamePlayerData;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPattern;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPatterns;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class JumpAndRun extends RaceGame<RaceGameArenaLayout> implements PlayerDamageTrigger {
    public JumpAndRun(Fest fest, ArenaInstance arenaInstance) {
        super(fest, arenaInstance);
    }

    private static final int CHECKPOINT_COUNT = 8;
    private static final int AVERAGE_JUMPS_PER_CHECKPOINT = 8;
    private static final int CHECKPOINT_VARIANCE = 2;

    private static final int BASE_EXTRA_DIFFICULTY = 3;
    private static final int LANE_HALF_WIDTH = 12;

    private static final int FALL_RESET_DISTANCE = 10;

    private final List<Placement> placements = new ArrayList<>();

    @Override
    public @NotNull GameType gameType() {
        return GameType.JUMP_AND_RUN;
    }

    @Override
    public Location determineOrigin() {
        Location origin = super.determineOrigin();
        origin.setY(origin.getWorld().getMaxHeight() - AVERAGE_JUMPS_PER_CHECKPOINT * CHECKPOINT_COUNT * 1.5);
        return origin;
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        Location origin = fest.origin().clone();
        World world = origin.getWorld();
        Random random = ThreadLocalRandom.current();

        JumpContext context = new JumpContext(random,
                (location, data) -> placements.add(new Placement(location, data)),
                origin.getBlockX() - LANE_HALF_WIDTH, origin.getBlockX() + LANE_HALF_WIDTH,
                world.getMinHeight() + 8, world.getMaxHeight() - 8);

        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                blockTracker.setBlock(context.offset(origin, x, -1, z), Material.QUARTZ_BLOCK);

        blockTracker.save();

        Location cursor = origin.clone().add(0, -1, 1);
        JumpPattern previous = null;

        for (int section = 0; section <= CHECKPOINT_COUNT; section++) {
            if (section > 0) {
                context.set(cursor, Material.GOLD_BLOCK);
                context.set(context.offset(cursor, 0, 1, 0), Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
            }

            //TODO: final checkpoint should also feature a pad or be more highlighted, beacon?

            checkPoints.add(new CheckPoint(context.offset(cursor, 0, 1, 0), 1, 3));

            if (section == CHECKPOINT_COUNT) break;

            int jumps = AVERAGE_JUMPS_PER_CHECKPOINT
                    + random.nextInt(CHECKPOINT_VARIANCE * 2 + 1) - CHECKPOINT_VARIANCE;
            int budget = jumps + BASE_EXTRA_DIFFICULTY + section;

            while (budget > 0) {
                JumpPattern pattern = JumpPatterns.pick(budget, section, previous, random);
                cursor = pattern.build(cursor, context);
                budget -= pattern.difficulty();
                previous = pattern;
            }
        }

        Set<ChunkPos> chunks = new HashSet<>();
        placements.forEach(placement ->
                chunks.add(new ChunkPos(placement.location().getBlockX() >> 4, placement.location().getBlockZ() >> 4)));

        CompletableFuture<Void> future = new CompletableFuture<>();
        loadChunks(world, chunks, _ -> {
            updateArenaBuildingBar(Component.text("Placing blocks"), 0.9f);

            placements.forEach(placement -> blockTracker.setBlock(placement.location(), placement.data()));

            blockTracker.save();
            updateArenaBuildingBar(Component.text("Waiting for server"), 1.0f);

            future.complete(null);
        });

        return future;
    }

    @Override
    protected void kickOff() {
        fest.participants().forEach(participant -> {
            participant.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
                if (!isRunning()) {
                    task.cancel();
                    return;
                }

                Location location = participant.getLocation();
                RaceGamePlayerData playerData = (RaceGamePlayerData) getOrCreatePlayerData(participant.getUniqueId());
                CheckPoint currentCheckPoint = checkPoints.get(playerData.currentCheckPoint());

                if (location.getY() > currentCheckPoint.location().y() - FALL_RESET_DISTANCE) return;

                participant.teleportAsync(currentCheckPoint.location());
                participant.playHurtAnimation(0.0f);
                participant.playSound(participant, Sound.ENTITY_GENERIC_HURT, 1f, 1f);
            }, null, 1, 1);
        });
    }

    protected Component startTitle() {
        return Component.text("Jump!", NamedTextColor.AQUA);
    }

    @Override
    protected void onPassCheckPoint(Player player, int checkPoint) {
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.85f);
    }

    @Override
    protected void onStop() {

    }

    private record Placement(Location location, BlockData data) {
    }

    @Override
    public void onPlayerDamage(EntityDamageEvent event, Player player) {
        event.setCancelled(true);
    }
}