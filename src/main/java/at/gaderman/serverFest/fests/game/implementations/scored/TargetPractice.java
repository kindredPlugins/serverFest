package at.gaderman.serverFest.fests.game.implementations.scored;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.implementations.scored.data.ScoreGamePlayerData;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import at.gaderman.serverFest.fests.game.interfaces.InvisiblePlayersGame;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import at.gaderman.serverFest.fests.game.triggers.prefabs.NoBlockManipulationPrefab;
import at.gaderman.serverFest.fests.game.triggers.prefabs.NoItemInventoryManipulation;
import at.gaderman.serverFest.fests.game.triggers.projectiles.PlayerProjectileHitTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TargetPractice extends ScoreGame<ArenaLayout> implements NoBlockManipulationPrefab, NoItemInventoryManipulation, PlayerProjectileHitTrigger, PlayerDamageTrigger,
        InvisiblePlayersGame {

    private Location shootCorner;
    private final List<ScoreTarget> scoreTargets = new ArrayList<>();
    private final Map<UUID, Set<ScoreTarget>> activeTargetMap = new HashMap<>();

    public TargetPractice(Fest fest, ArenaInstance arenaInstance) {
        super(fest, arenaInstance);
    }

    @Override
    public @NotNull GameType gameType() {
        return GameType.TARGET_PRACTICE;
    }

    @Override
    public Location determineOrigin() {
        Location origin = super.determineOrigin();
        origin.setY(fest.world().getHighestBlockYAt(origin) + 1);
        return origin;
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        return buildArena();
    }

    @Override
    public void onPlayersJoinedWorld() {
        super.onPlayersJoinedWorld();

        InvisiblePlayersGame.super.grantInvisibilityToPlayers(fest.participants());
        fest.participants().forEach(player -> player.teleport(fest.origin().toCenterLocation()));
    }

    @Override
    protected void afterWinner(List<Player> winner) {
        List<Player> players = fest.participants();
        fest.world().getEntitiesByClass(Arrow.class).forEach(arrow -> {
            if (arrow.getShooter() instanceof Player shooter && players.contains(shooter))
                arrow.remove();
        });
    }

    @Override
    protected void kickOff() {
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.INFINITY, 1);

        fest.participants().forEach(player -> {
            Inventory inventory = player.getInventory();

            inventory.setItem(0, bow);
            inventory.setItem(inventory.getSize() - 1, ItemStack.of(Material.ARROW));
        });

        spawnScatteredTargets();
        updateScoreboard();
    }

    @Override
    protected int runTime() {
        return 30 * 20;
    }

    // IMPORTANT: TargetPractice has no need for blockTracker conceptually, since it clears its are for itself everytime
    // However, further versions could change something about TargetPractice which might not be cleared correctly then
    // So BlockTracker will still be used
    @Override
    protected @NonNull CompletableFuture<Void> onReset() {
        return CompletableFuture.completedFuture(null);
    }

    //region Visuals

    @Override
    protected Component startTitle() {
        return Component.text("Shoot Targets!", NamedTextColor.RED);
    }

    @Override
    protected Component bossBarTitle() {
        return Component.text("Shoot Targets!", NamedTextColor.RED);
    }

    //endregion

    //region Arena

    private static final int ARENA_WIDTH = 20;
    private static final int ARENA_HEIGHT = 20;
    private static final int ARENA_LENGTH = 55;

    private static final int PLATFORM_OFFSET = 14;
    private static final int PLATFORM_WIDTH = 4;
    private static final int PLATFORM_LENGTH = 2;

    private CompletableFuture<Void> buildArena() {
        World world = fest.world();
        Location origin = fest.origin();

        int arenaWidth = ARENA_WIDTH / 2;
        int minX = origin.getBlockX() - arenaWidth;
        int maxX = origin.getBlockX() + arenaWidth;
        int minZ = origin.getBlockZ() - PLATFORM_LENGTH;
        int maxZ = origin.getBlockZ() + ARENA_LENGTH - 1;

        Set<Integer> chunkZs = new HashSet<>();
        for (int z = minZ; z <= maxZ; z++)
            chunkZs.add(z >> 4);

        int chunkX = origin.getBlockX() >> 4;
        List<CompletableFuture<Chunk>> chunks = chunkZs.stream()
                .map(chunkZ -> world.getChunkAtAsync(chunkX, chunkZ))
                .toList();

        return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).thenRun(() -> {
            boolean doBlockDrops = world.getGameRuleValue(GameRules.BLOCK_DROPS);
            world.setGameRule(GameRules.BLOCK_DROPS, false);

            for (int x = minX; x <= maxX; x++)
                for (int y = 0; y < ARENA_HEIGHT; y++)
                    for (int z = minZ; z <= maxZ; z++) {
                        boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                        Material clearMat = edge ? Material.STRUCTURE_VOID : Material.AIR;

                        blockTracker.setBlock(world.getBlockAt(x, origin.getBlockY() - 1 + y, z), clearMat);
                    }

            for (int x = 0; x < PLATFORM_WIDTH * 2 + 1; x++)
                for (int z = 0; z < PLATFORM_LENGTH + 2; z++) {
                    Location place = new Location(world, origin.x() + x - PLATFORM_WIDTH, origin.getY() - 1, origin.z() + z - PLATFORM_LENGTH);
                    blockTracker.setBlock(place, Material.BAMBOO_MOSAIC);

                    if (x == 0 || z == 0 || x == PLATFORM_WIDTH * 2 || z == PLATFORM_LENGTH * 2 - 1)
                        blockTracker.setBlock(place.clone().add(0, 1, 0), Material.BAMBOO_FENCE);
                }

            this.shootCorner = new Location(world, origin.getX() - arenaWidth, origin.getY() - 1, origin.getZ() + 12);
            for (int x = 0; x <= arenaWidth * 2; x++)
                for (int z = 0; z <= ARENA_LENGTH - PLATFORM_OFFSET; z++)
                    blockTracker.setBlock(shootCorner.clone().add(x, 0, z), Material.YELLOW_GLAZED_TERRACOTTA);

            world.setGameRule(GameRules.BLOCK_DROPS, doBlockDrops);
            blockTracker.save();
        });
    }

    private void spawnScatteredTargets() {
        int chunkSize = 4;
        int rows = (ARENA_LENGTH - PLATFORM_OFFSET) / chunkSize;

        List<Integer> usedXPositions = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            int zAmount = Math.random() < 0.5 ? 1 : 2;

            int x = (int) (Math.random() * ARENA_WIDTH - 2) + 1;
            int y = (int) (Math.random() * 8);
            int z = i * chunkSize + zAmount;

            int tries = 0;
            while (usedXPositions.contains(x) && tries < ARENA_WIDTH - 1) {
                x++;
                if (x == ARENA_WIDTH - 1)
                    x = 0;

                tries++;
            }

            Location targetLoc = this.shootCorner.clone().add(x, y, z);
            createTarget(targetLoc);
            //IMPORTANT: this vector here is for the target block, if the target constellation changes this will break score gains!!!
            scoreTargets.add(new ScoreTarget(targetLoc.clone().add(0, 2, 0), i * chunkSize + zAmount));

            usedXPositions.add(x);
        }
        blockTracker.save();
    }

    private void createTarget(Location location) {
        blockTracker.setBlock(location.clone().add(0, 1, 0), Material.BAMBOO_FENCE);

        blockTracker.setBlock(location.clone().add(1, 2, 0), Material.BAMBOO_FENCE);
        blockTracker.setBlock(location.clone().add(-1, 2, 0), Material.BAMBOO_FENCE);
        blockTracker.setBlock(location.clone().add(0, 2, 0), Material.TARGET);
        blockTracker.setBlock(location.clone().add(0, 3, 0), Material.CARVED_PUMPKIN);

        blockTracker.setBlock(location.clone().add(0, 2, -1), Material.IRON_TRAPDOOR);
    }

    //endregion

    //region Event Handling

    @Override
    public void onPlayerDamage(EntityDamageEvent event, Player player) {
        event.setCancelled(true);
    }

    @Override
    public void onPlayerProjectileHit(ProjectileHitEvent event, Projectile projectile, Player player) {
        Block block = event.getHitBlock();

        if (event.getHitEntity() != null) {
            event.setCancelled(true);
            return;
        }

        if (block == null || block.getType() != Material.TARGET) {
            projectile.setTicksLived(60 * 20 - 10);
            return;
        }

        ScoreTarget scoreTarget = scoreTargets.stream()
                .filter(target -> target.target.distance(block.getLocation()) < 1).findFirst().orElse(null);

        if (scoreTarget == null)
            return;

        Set<ScoreTarget> activeTargets = this.activeTargetMap.getOrDefault(player.getUniqueId(), new HashSet<>());
        if (activeTargets.contains(scoreTarget)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        var playerData = (ScoreGamePlayerData) getOrCreatePlayerData(player.getUniqueId());
        int scoreGain = determineScore(projectile, block) + scoreTarget.rowBonus;
        playerData.setScore(playerData.score() + scoreGain);
        updateScoreboard();

        player.sendActionBar(Component.text("+" + scoreGain + " ◈", NamedTextColor.GREEN));

        toggleTarget(block.getLocation(), player, true);

        player.getScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            if (!fest.game().isRunning())
                return;

            Set<ScoreTarget> targets = this.activeTargetMap.getOrDefault(player.getUniqueId(), new HashSet<>());
            targets.remove(scoreTarget);
            this.activeTargetMap.put(player.getUniqueId(), targets);

            player.getScheduler().run(ServerFest.getPlugin(), _ -> {
                toggleTarget(block.getLocation(), player, false);
            }, null);
        }, null, 100);

        player.playSound(player, Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);
        projectile.remove();

        activeTargets.add(scoreTarget);
        this.activeTargetMap.put(player.getUniqueId(), activeTargets);
    }

    private int determineScore(Projectile projectile, Block block) {
        double dx = projectile.getX() - (block.getX() + 0.5);
        double dy = projectile.getY() - (block.getY() + 0.5);
        double distanceSquared = dx * dx + dy * dy;

        if (distanceSquared < 0.0225)
            return 4;
        else if (distanceSquared < 0.09)
            return 3;
        else if (distanceSquared < 0.2025)
            return 2;
        else
            return 1;
    }

    private void toggleTarget(Location targetLocation, Player player, boolean activated) {
        AnaloguePowerable target = (AnaloguePowerable) Material.TARGET.createBlockData();
        target.setPower(activated ? 15 : 0);
        player.sendBlockChange(targetLocation, target);

        Location gateLocation = targetLocation.clone().add(0, 0, -1);
        TrapDoor gate = (TrapDoor) gateLocation.getBlock().getBlockData();
        gate.setOpen(activated);
        player.sendBlockChange(gateLocation, gate);

        player.sendBlockChange(targetLocation.clone().add(0, 1, 0), activated
                ? Material.JACK_O_LANTERN.createBlockData()
                : Material.CARVED_PUMPKIN.createBlockData());
    }

    //endregion

    @Override
    protected @NonNull FestPlayerData createPlayerData(UUID uuid) {
        return new ScoreGamePlayerData(uuid);
    }

    private record ScoreTarget(Location target, int rowBonus) {
    }
}
