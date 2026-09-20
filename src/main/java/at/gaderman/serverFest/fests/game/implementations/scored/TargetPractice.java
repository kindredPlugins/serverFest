package at.gaderman.serverFest.fests.game.implementations.scored;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.implementations.scored.data.ScoreGamePlayerData;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import at.gaderman.serverFest.fests.game.triggers.prefabs.NoBlockManipulationPrefab;
import at.gaderman.serverFest.fests.game.triggers.projectiles.PlayerProjectileHitTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Observer;
import org.bukkit.block.data.type.Piston;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TargetPractice extends ScoreGame implements NoBlockManipulationPrefab, PlayerProjectileHitTrigger, PlayerDamageTrigger {

    private Location shootCorner;
    private final List<ScoreTarget> scoreTargets = new ArrayList<>();

    public TargetPractice(Fest fest) {
        super(fest);
    }

    @Override
    public Material iconType() {
        return Material.TARGET;
    }

    @Override
    public Component iconName() {
        return Component.text("Target Practice", NamedTextColor.RED);
    }

    @Override
    public List<Component> iconLore() {
        return List.of();
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        World world = fest.world();

        Location spawnPoint = new Location(world, fest.offset(), world.getSeaLevel(), 0).toCenterLocation();
        spawnPoint.setY(world.getHighestBlockAt(spawnPoint).getY() + 1);

        world.setSpawnLocation(spawnPoint);

        CompletableFuture<Void> future = new CompletableFuture<>();
        buildArena(future);
        return future;
    }

    @Override
    public void onPlayersJoinedWorld() {

    }

    @Override
    protected void onWinner(List<Player> winner) {
        super.onWinner(winner);

        List<Player> players = fest.participants();
        fest.world().getEntitiesByClass(Arrow.class).forEach(arrow -> {
            if(arrow.getShooter() instanceof Player shooter && players.contains(shooter))
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

    @Override
    protected void onStop() {
        fest.participants().forEach(player -> player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()));
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

    private void buildArena(CompletableFuture<Void> future) {
        World world = fest.world();
        Location origin = world.getSpawnLocation();

        world.getChunkAtAsync(origin).thenAccept(_ -> {
            boolean doBlockDrops = world.getGameRuleValue(GameRules.BLOCK_DROPS);
            world.setGameRule(GameRules.BLOCK_DROPS, false);

            int arenaWidth = ARENA_WIDTH / 2;

            int minX = (int) origin.getX() - arenaWidth;
            int maxX = (int) origin.getX() + arenaWidth;
            int minZ = (int) origin.getZ() - PLATFORM_LENGTH;
            int maxZ = (int) origin.getZ() + ARENA_LENGTH - 1;

            for (int x = minX; x <= maxX; x++)
                for (int y = 0; y < ARENA_HEIGHT; y++)
                    for (int z = minZ; z <= maxZ; z++)
                        world.getBlockAt(x, origin.getBlockY() - 1 + y, z).setType(Material.AIR);

            for (int x = 0; x < PLATFORM_WIDTH * 2 + 1; x++)
                for (int z = 0; z < PLATFORM_LENGTH + 2; z++) {
                    Location place = new Location(world, origin.x() + x - PLATFORM_WIDTH, origin.getY() - 1, origin.z() + z - PLATFORM_LENGTH);
                    place.getBlock().setType(Material.BAMBOO_MOSAIC);

                    if (x == 0 || z == 0 || x == PLATFORM_WIDTH * 2 || z == PLATFORM_LENGTH * 2 - 1)
                        place.clone().add(0, 1, 0).getBlock().setType(Material.BAMBOO_FENCE);
                }

            this.shootCorner = new Location(world, origin.getX() - arenaWidth, origin.getY() - 1, origin.getZ() + 12);
            for (int x = 0; x <= arenaWidth * 2; x++)
                for (int z = 0; z <= ARENA_LENGTH - PLATFORM_OFFSET; z++)
                    shootCorner.clone().add(x, 0, z).getBlock().setType(Material.YELLOW_GLAZED_TERRACOTTA);

            world.setGameRule(GameRules.BLOCK_DROPS, doBlockDrops);
            future.complete(null);
        });
    }

    private void spawnScatteredTargets() {
        int chunkSize = 4;
        int rows = (ARENA_LENGTH - PLATFORM_OFFSET) / chunkSize;

        for (int i = 0; i < rows; i++) {
            int zAmount = Math.random() < 0.5 ? 1 : 2;

            int x = (int) (Math.random() * ARENA_WIDTH - 1) + 1;
            int y = (int) (Math.random() * 8);
            int z = i * chunkSize + zAmount;

            Location targetLoc = this.shootCorner.clone().add(x, y, z);
            createTarget(targetLoc);
            //IMPORTANT: this vector here is for the target block, if the target constellation changes this will break distance based score gains!!!
            scoreTargets.add(new ScoreTarget(targetLoc.clone().add(0, 2, 1), i * chunkSize + zAmount));
        }
    }

    private void createTarget(Location location) {
        Block start = location.getBlock();
        start.setType(Material.STICKY_PISTON);
        Piston piston = (Piston) start.getBlockData();
        piston.setFacing(BlockFace.UP);
        start.setBlockData(piston);

        location.clone().add(0, 1, 0).getBlock().setType(Material.REDSTONE_LAMP);

        location.clone().add(0, 0, 1).getBlock().setType(Material.NOTE_BLOCK);

        location.clone().add(0, 2, 1).getBlock().setType(Material.TARGET);
        location.clone().add(0, 3, 1).getBlock().setType(Material.CARVED_PUMPKIN);

        Block fenceLeft = location.clone().add(1, 2, 1).getBlock();
        fenceLeft.setType(Material.BAMBOO_FENCE);
        fenceLeft.tick();
        Block fenceRight = location.clone().add(-1, 2, 1).getBlock();
        fenceRight.setType(Material.BAMBOO_FENCE);
        fenceRight.tick();

        Block observer = location.clone().add(0, 1, 1).getBlock();
        observer.setType(Material.OBSERVER);
        Observer observerData = (Observer) observer.getBlockData();
        observerData.setFacing(BlockFace.DOWN);
        observer.setBlockData(observerData);
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

        Bukkit.getServer().getRegionScheduler().runDelayed(ServerFest.getPlugin(), block.getLocation(), _ -> {
            if (!fest.game().isRunning())
                return;

            AnaloguePowerable powerable = (AnaloguePowerable) block.getBlockData();
            ScoreTarget scoreTarget = scoreTargets.stream()
                    .filter(target -> target.target.distance(block.getLocation()) < 1).findFirst().orElse(null);

            var playerData = (ScoreGamePlayerData) getOrCreatePlayerData(player.getUniqueId());
            int scoreGain = powerable.getPower() + (scoreTarget == null ? 0 : scoreTarget.rowBonus);
            playerData.setScore(playerData.score() + scoreGain);
            updateScoreboard();

            player.sendActionBar(Component.text("+" + scoreGain + " ◈", NamedTextColor.GREEN));
        }, 1);

        player.playSound(player, Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);
        projectile.remove();

        Location pistonLocation = block.getLocation().clone().add(0, -2, -1);
        pulsePiston(pistonLocation);

        Bukkit.getServer().getRegionScheduler().runDelayed(ServerFest.getPlugin(), pistonLocation, _ -> {
            if (!fest.game().isRunning())
                return;

            pulsePiston(pistonLocation);
        }, 100);
    }

    private void pulsePiston(Location pistonLocation) {
        Block pulseBlock = pistonLocation.clone().add(0, -1, 0).getBlock();
        BlockData blockData = pulseBlock.getBlockData().clone();

        pulseBlock.setType(Material.REDSTONE_BLOCK);

        Bukkit.getServer().getRegionScheduler().runDelayed(ServerFest.getPlugin(), pulseBlock.getLocation(), _ -> {
            if (!fest.game().isRunning())
                return;

            pulseBlock.setBlockData(blockData);
        }, 2);
    }


    //endregion

    @Override
    protected @NonNull FestPlayerData createPlayerData(UUID uuid) {
        return new ScoreGamePlayerData(uuid);
    }

    private record ScoreTarget(Location target, int rowBonus) {
    }
}
