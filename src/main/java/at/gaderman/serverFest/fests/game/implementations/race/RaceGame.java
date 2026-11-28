package at.gaderman.serverFest.fests.game.implementations.race;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.implementations.race.arena.RaceGameArenaLayout;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;
import at.gaderman.serverFest.fests.game.implementations.race.data.RaceGamePlayerData;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import at.gaderman.serverFest.fests.game.interfaces.InvisiblePlayersGame;
import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerInventoryClickTrigger;
import at.gaderman.serverFest.fests.game.triggers.prefabs.NoBlockManipulationPrefab;
import at.gaderman.serverFest.fests.game.triggers.prefabs.NoFoodLevelChangePrefab;
import at.gaderman.serverFest.fests.game.triggers.prefabs.NoItemInventoryManipulation;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public abstract class RaceGame<T extends ArenaLayout> extends FestGame<T> implements NoBlockManipulationPrefab, NoItemInventoryManipulation, NoFoodLevelChangePrefab,
        PlayerInventoryClickTrigger, InvisiblePlayersGame {

    protected enum CheckPointValidation {
        /// default for RaceGame, checks the users last coordinates and their current and looks if they passed through the checkpoint
        PASS_THROUGH,
        /// changes the checkpoint test to continuously check if the current position is within the checkpoint box, useful suited for slow-paced games
        BOX_CHECK
    }

    protected List<CheckPoint> checkPoints;

    public RaceGame(Fest fest, ArenaInstance arenaInstance) {
        super(fest, arenaInstance);
        if (arenaLayout instanceof RaceGameArenaLayout raceGameArenaLayout)
            checkPoints = raceGameArenaLayout.checkPoints();

        else checkPoints = new ArrayList<>();
    }

    //region Overrides of FestGame

    @Override
    public void onPlayersJoinedWorld() {
        WorldBorder startBorder = Bukkit.createWorldBorder();
        startBorder.setSize(1.5);
        startBorder.setCenter(fest.origin());

        fest.participants().forEach(participant -> {
            participant.setWorldBorder(startBorder);
            participant.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
        });

        InvisiblePlayersGame.super.grantInvisibilityToPlayers(fest.participants());
    }

    @Override
    protected void onCountdownComplete() {
        fest.participants().forEach(this::updateBossbar);
        activateCheckPointDetection();
        kickOff();
    }

    @Override
    protected @NonNull CompletableFuture<Void> onReset() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        visualEntities.forEach(Entity::remove);
        future.complete(null);
        return future;
    }

    //endregion

    //region Abstract methods

    protected Component startTitle() {
        return Component.text("Race!", NamedTextColor.GOLD);
    }

    protected abstract void kickOff();

    protected abstract void onPassCheckPoint(Player player, int checkPoint);

    protected CheckPointValidation checkPointValidationType() {
        return CheckPointValidation.PASS_THROUGH;
    }

    //endregion

    //region Checkpoint Logic

    private static final boolean FORWARD_ONLY = false;
    private static final double TELEPORT_DISTANCE_SQ = 10.0 * 10.0;

    private static final int WRONG_WAY_TITLE_INTERVAL = 4;
    private static final Title WRONG_WAY_TITLE = Title.title(Component.text("↶", NamedTextColor.RED), Component.empty(),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(100), Duration.ofMillis(100)));

    /**
     * Optimized collection of data needed for continuous checks
     */
    private record Gate(double x, double y, double z, double sin, double cos, double halfWidth, double halfHeight) {

        static Gate of(CheckPoint checkPoint) {
            Location location = checkPoint.location();
            double radians = Math.toRadians(location.getYaw());

            return new Gate(location.getX(), location.getY(), location.getZ(), Math.sin(radians), Math.cos(radians),
                    checkPoint.width() / 2.0, checkPoint.height() / 2.0);
        }

        /**
         * Distance along the direction the gate faces: negative before the gate plane, positive after it.
         */
        double localZ(double px, double pz) {
            return -(px - x) * sin + (pz - z) * cos;
        }

        /**
         * Distance sideways across the gate opening, 0 in the middle.
         */
        double localX(double px, double pz) {
            return (px - x) * cos + (pz - z) * sin;
        }
    }

    /**
     * Per-player state of the detection. Only ever touched on that player's own thread.
     */
    private static final class Tracker {
        final Player player;
        final RaceGamePlayerData data;

        double prevX, prevY, prevZ;
        boolean hasPrev;
        int wrongWayCooldown;

        Tracker(Player player, RaceGamePlayerData data) {
            this.player = player;
            this.data = data;
        }

        void remember(double x, double y, double z) {
            prevX = x;
            prevY = y;
            prevZ = z;
            hasPrev = true;
        }

        double distanceSquaredTo(double x, double y, double z) {
            double dx = x - prevX, dy = y - prevY, dz = z - prevZ;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private void activateCheckPointDetection() {
        Gate[] gates = checkPoints.stream().map(Gate::of).toArray(Gate[]::new);
        var validation = checkPointValidationType();

        fest.participants().forEach(participant -> {
            Tracker tracker = new Tracker(participant, (RaceGamePlayerData) getOrCreatePlayerData(participant.getUniqueId()));

            Consumer<ScheduledTask> check = switch (validation) {
                case PASS_THROUGH -> task -> passThroughTick(task, tracker, gates);
                case BOX_CHECK -> task -> boxTick(task, tracker, gates);
            };

            participant.getScheduler().runAtFixedRate(ServerFest.getPlugin(), check, null, 1, 1);
        });
    }

    private void passThroughTick(ScheduledTask task, Tracker tracker, Gate[] gates) {
        if (!isRunning()) {
            task.cancel();
            return;
        }

        int target = tracker.data.currentCheckPoint() + 1;
        if (target >= gates.length) {
            finish(task, tracker);
            return;
        }

        Player player = tracker.player;
        double x = player.getX(), y = player.getY(), z = player.getZ();

        if (!tracker.hasPrev || tracker.distanceSquaredTo(x, y, z) > TELEPORT_DISTANCE_SQ) {
            tracker.remember(x, y, z);
            return;
        }

        Gate gate = gates[target];
        double prevLz = gate.localZ(tracker.prevX, tracker.prevZ);
        double currLz = gate.localZ(x, z);

        boolean passed = changesSide(prevLz, currLz) && throughOpening(gate, tracker, x, y, z, prevLz, currLz);
        tracker.remember(x, y, z);

        if (passed)
            advance(task, tracker, target, gates.length);
        else if (currLz > 0)
            warnWrongWay(tracker);
    }

    private void boxTick(ScheduledTask task, Tracker tracker, Gate[] gates) {
        if (!isRunning()) {
            task.cancel();
            return;
        }

        int target = tracker.data.currentCheckPoint() + 1;
        if (target >= gates.length) {
            finish(task, tracker);
            return;
        }

        Player player = tracker.player;
        Gate gate = gates[target];

        if (Math.abs(player.getX() - gate.x()) <= gate.halfWidth()
                && Math.abs(player.getY() - gate.y()) <= gate.halfHeight()
                && Math.abs(player.getZ() - gate.z()) <= gate.halfWidth())
            advance(task, tracker, target, gates.length);
    }

    private static boolean changesSide(double prevLz, double currLz) {
        if (FORWARD_ONLY)
            return prevLz < 0 && currLz >= 0;

        return !((prevLz < 0 && currLz < 0) || (prevLz > 0 && currLz > 0));
    }

    /**
     * The movement touches the gate plane, now check whether that happens inside the opening.
     */
    private static boolean throughOpening(Gate gate, Tracker tracker, double x, double y, double z,
                                          double prevLz, double currLz) {
        double dz = currLz - prevLz;
        if (Math.abs(dz) < 1e-9)
            return false;

        // Where on the movement the plane is hit. Always within 0..1 because the sides differ, no range check needed.
        double t = -prevLz / dz;

        double prevLx = gate.localX(tracker.prevX, tracker.prevZ);
        double currLx = gate.localX(x, z);

        double crossingX = prevLx + (currLx - prevLx) * t;
        double crossingY = (tracker.prevY - gate.y()) + (y - tracker.prevY) * t;

        return Math.abs(crossingX) <= gate.halfWidth() && Math.abs(crossingY) <= gate.halfHeight();
    }

    private void advance(ScheduledTask task, Tracker tracker, int reached, int gateCount) {
        tracker.data.setCheckPoint(reached);
        updateBossbar(tracker.player);
        onPassCheckPoint(tracker.player, reached);

        if (reached + 1 >= gateCount)
            finish(task, tracker);
    }

    private void finish(ScheduledTask task, Tracker tracker) {
        task.cancel();
        declareWinner(List.of(tracker.player));
    }

    private void warnWrongWay(Tracker tracker) {
        if (tracker.wrongWayCooldown-- > 0)
            return;

        tracker.wrongWayCooldown = WRONG_WAY_TITLE_INTERVAL - 1;
        tracker.player.showTitle(WRONG_WAY_TITLE);
    }

//endregion

    //region Visuals

    private final List<Entity> visualEntities = new ArrayList<>();

    private void updateBossbar(Player participant) {
        RaceGamePlayerData playerData = (RaceGamePlayerData) getOrCreatePlayerData(participant.getUniqueId());

        Component title = Component.text("Checkpoint ", NamedTextColor.GREEN)
                .append(Component.text(playerData.currentCheckPoint(), NamedTextColor.GOLD))
                .append(Component.text("/" + (checkPoints.size() - 1), NamedTextColor.GRAY));

        float progress = 1.0f * playerData.currentCheckPoint() / (checkPoints.size() - 1);
        playerData.updateBossBar(participant, title, progress, BossBar.Color.GREEN);
    }

    protected void createGate(Location center, double size, double thickness, boolean finish) {
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

                display.setPersistent(false);
                visualEntities.add(display);
            });
        });
    }

    //endregion

    @Override
    protected @NonNull FestPlayerData createPlayerData(UUID uuid) {
        return new RaceGamePlayerData(uuid);
    }
}
