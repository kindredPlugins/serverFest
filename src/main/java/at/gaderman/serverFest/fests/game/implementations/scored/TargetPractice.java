package at.gaderman.serverFest.fests.game.implementations.scored;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.implementations.scored.data.ScoreGamePlayerData;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import at.gaderman.serverFest.fests.game.triggers.prefabs.NoBlockManipulationPrefab;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Observer;
import org.bukkit.block.data.type.Piston;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TargetPractice extends FestGame implements NoBlockManipulationPrefab {

    private Location shootCorner;

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
    protected void onStart() {
        final int[] timeLeft = {7}; // 7 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                timeLeft[0]--;

                if (timeLeft[0] <= 5)
                    sendTitle(timeLeft[0]);

                if (timeLeft[0] <= 0) {
                    cancel();

                    fest.participants().forEach(participant -> {
                        participant.setWorldBorder(fest.world().getWorldBorder());
                        participant.playSound(participant, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                        participant.sendTitlePart(TitlePart.TITLE, Component.text("Shoot!", NamedTextColor.GOLD));
                    });

                    ItemStack bow = new ItemStack(Material.BOW);
                    bow.addEnchantment(Enchantment.INFINITY, 1);

                    fest.participants().forEach(player -> {
                        Inventory inventory = player.getInventory();

                        inventory.setItem(0, bow);
                        inventory.setItem(inventory.getSize() - 1, ItemStack.of(Material.ARROW));
                    });

                    spawnScatteredTargets();
                }

            }
        }.runTaskTimer(ServerFest.getPlugin(), 20L, 20L);

        Bukkit.getServer().getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            stop();
        }, 1200);
    }

    private void sendTitle(long timeLeft) {
        fest.participants().forEach(participant -> {
            participant.playSound(participant, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            participant.sendTitlePart(TitlePart.TITLE, Component.text(timeLeft, NamedTextColor.GREEN));
        });
    }

    @Override
    protected void onWinner(List<Player> winner) {

    }

    @Override
    protected void onStop() {

    }

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

            createTarget(this.shootCorner.clone().add(x, y, z));
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
        location.clone().add(1, 2, 1).getBlock().setType(Material.BAMBOO_FENCE);
        location.clone().add(-1, 2, 1).getBlock().setType(Material.BAMBOO_FENCE);

        Block observer = location.clone().add(0, 1, 1).getBlock();
        observer.setType(Material.OBSERVER);
        Observer observerData = (Observer) observer.getBlockData();
        observerData.setFacing(BlockFace.UP);
        observer.setBlockData(observerData);
    }

    //endregion

    @Override
    protected @NonNull FestPlayerData createPlayerData(UUID uuid) {
        return new ScoreGamePlayerData();
    }
}
