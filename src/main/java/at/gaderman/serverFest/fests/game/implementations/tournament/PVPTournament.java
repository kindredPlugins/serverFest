package at.gaderman.serverFest.fests.game.implementations.tournament;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.game.FestGame;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import at.gaderman.serverFest.fests.game.triggers.action.PlayerDeathTrigger;
import at.gaderman.serverFest.fests.game.triggers.damage.PlayerDamageTrigger;
import at.gaderman.serverFest.fests.game.triggers.prefabs.NoBlockManipulationPrefab;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PVPTournament extends FestGame<ArenaLayout> implements NoBlockManipulationPrefab,
        PlayerDamageTrigger, PlayerDeathTrigger {

    private final List<FaceOff> activeFaceOffs = new ArrayList<>();
    private final List<Arena> arenas = new ArrayList<>();

    public PVPTournament(Fest fest, ArenaInstance arenaInstance) {
        super(fest, arenaInstance);
    }

    @Override
    public @NotNull GameType gameType() {
        return GameType.PVP_TOURNAMENT;
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        groupIntoFaceOffs();
        createWaitingArena();
        return buildArenas();
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
                        participant.sendTitlePart(TitlePart.TITLE, Component.text("Race!", NamedTextColor.GOLD));

                        //updateBossbar(participant);
                    });
                    kickOff();
                }

            }
        }.runTaskTimer(ServerFest.getPlugin(), 20L, 20L);
    }

    private void sendTitle(long timeLeft) {
        fest.participants().forEach(participant -> {
            participant.playSound(participant, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            participant.sendTitlePart(TitlePart.TITLE, Component.text(timeLeft, NamedTextColor.GREEN));
        });
    }

    private void kickOff() {
        startFaceOffs();
    }

    @Override
    protected void onStop() {

    }

    @Override
    protected CompletableFuture<Void> onReset() {
        return null;
    }

    //TODO: common single winner interface instead of copypasta
    @Override
    protected void onWinner(List<Player> winners) {
        Player singleWinner = winners.getFirst();

        fest.participants().forEach(participant -> {
            boolean isWinner = winners.contains(participant);
            participant.resetTitle();

            if (isWinner) {
                participant.sendTitlePart(TitlePart.TITLE, Component.text("Winner!", NamedTextColor.GOLD));
                participant.getWorld().playSound(participant, Sound.MUSIC_DISC_PIGSTEP, 1f, 1.3f);

                participant.getWorld().spawn(participant.getLocation(), Firework.class, firework -> {
                    FireworkMeta meta = firework.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(Color.GREEN)
                            .with(FireworkEffect.Type.CREEPER)
                            .withFlicker()
                            .withTrail()
                            .withFade(Color.LIME)
                            .build());

                    firework.setFireworkMeta(meta);
                });
            } else {
                participant.sendTitlePart(TitlePart.TITLE, winners.getFirst().displayName().color(NamedTextColor.AQUA)
                        .append(Component.text(" has won!", NamedTextColor.GREEN)));
                participant.playSound(participant, Sound.ENTITY_WITHER_DEATH, 1f, 1.3f);
            }

            participant.sendTitlePart(TitlePart.SUBTITLE, MiniMessage.miniMessage().deserialize("<head:" + singleWinner.getUniqueId() + "> ")
                    .append(singleWinner.name().color(NamedTextColor.GOLD))
                    .append(Component.text(" has won!", NamedTextColor.GREEN)));
        });

        Bukkit.getServer().getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            stop();
        }, 200);
    }

    //region Organization

    //TODO: for testing purposes to be able to play alone, remote later
    private int bonusFaceOffs = fest.participants().size() > 1 ? 0 : 1;

    private void groupIntoFaceOffs() {
        List<Player> participants = fest.participants().stream()
                .filter(player -> ((TournamentData) getOrCreatePlayerData(player.getUniqueId())).state() == PlayerTournamentState.WAITING)
                .toList();

        for (int i = 0; i < participants.size() / 2; i++) {
            int index = i * 2;

            activeFaceOffs.add(new FaceOff(participants.get(index), participants.get(index + 1)));
        }

        if(bonusFaceOffs > 0){
            activeFaceOffs.add(new FaceOff(participants.getFirst(), participants.getFirst()));
        }
        bonusFaceOffs--;
    }

    private boolean checkIfFaceOffWinnerIsAlreadyDecided() {
        if (activeFaceOffs.isEmpty()) {
            List<Player> winners = fest.participants().stream().filter(participant -> {
                TournamentData tournamentData = (TournamentData) getOrCreatePlayerData(participant.getUniqueId());
                return tournamentData.state() == PlayerTournamentState.WAITING;
            }).toList();

            if (winners.isEmpty()) {
                //TODO: handle draw case I guess, what happens when last two players time out
                stop();
                return true;
            }

            declareWinner(winners);
            return true;
        }

        return false;
    }

    private void startFaceOffs() {
        if (checkIfFaceOffWinnerIsAlreadyDecided())
            return;

        for (int i = 0; i < activeFaceOffs.size(); i++) {
            FaceOff faceOff = activeFaceOffs.get(i);
            Arena arena = arenas.get(i);

            faceOff.player1.teleport(arena.player1Spawn);
            faceOff.player2.teleport(arena.player2Spawn);

            ((TournamentData) getOrCreatePlayerData(faceOff.player1.getUniqueId())).setState(PlayerTournamentState.BATTLE);
            ((TournamentData) getOrCreatePlayerData(faceOff.player2.getUniqueId())).setState(PlayerTournamentState.BATTLE);

            faceOff.players().forEach(player -> {
                player.resetTitle();
                player.sendTitlePart(TitlePart.TITLE, Component.text("Fight!", NamedTextColor.RED));
                player.playSound(player, Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1.3f);

                quipWithKit(player);
            });
        }

        startBattlePhase();
    }

    /**
     * Handles removing an active faceOff in a way where it will automatically trigger the next round if it was the last to finish
     */
    private void finishFaceOff(FaceOff faceOff) {
        activeFaceOffs.remove(faceOff);

        faceOff.players().forEach(both -> {
            both.getScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                both.getInventory().clear();
                both.teleportAsync(fest.origin().toCenterLocation());
            }, null, 20);
        });

        if (activeFaceOffs.isEmpty()) {
            int initialDelay = 30;
            int delay = 100;

            Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                groupIntoFaceOffs();

                if (checkIfFaceOffWinnerIsAlreadyDecided())
                    return;

                fest.participants().forEach(participant -> {
                    participant.playSound(participant, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.35f);
                    participant.resetTitle();
                    participant.sendTitlePart(TitlePart.TITLE, Component.text("Round finish", NamedTextColor.GOLD));
                    participant.sendTitlePart(TitlePart.SUBTITLE, Component.text("Continuing in " + delay / 20 + " seconds", NamedTextColor.GRAY));
                });
            }, initialDelay);

            Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                if (activeFaceOffs.isEmpty())
                    return;

                startFaceOffs();
            }, initialDelay + delay);
        }
    }

    private static final int BATTLE_TIME = 30 * 20;
    private int remainingBattleTicks;

    private void startBattlePhase() {
        remainingBattleTicks = BATTLE_TIME;

        //TODO: add proper folia regions for players themselves
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            if (remainingBattleTicks <= 0) {

                activeFaceOffs.stream().toList().forEach(faceOff -> {
                    Player loser = null;

                    if (faceOff.player1.getHealth() > faceOff.player2.getHealth())
                        loser = faceOff.player1;
                    else if (faceOff.player2.getHealth() > faceOff.player1.getHealth())
                        loser = faceOff.player2;

                    if (loser != null) {
                        loser.kill();
                        return;
                    }

                    finishFaceOff(faceOff);
                });

                task.cancel();
                return;
            }

            if (activeFaceOffs.isEmpty()) {
                task.cancel();
                return;
            }

            if (remainingBattleTicks % 20 == 0 && remainingBattleTicks <= 100)
                activeFaceOffs.stream().flatMap(faceOff -> faceOff.players().stream())
                        .forEach(player -> {
                            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.1f);
                            player.damage(1);
                        });

            remainingBattleTicks--;
        }, 1, 1);
    }

    //endregion

    //region Player Options

    private void quipWithKit(Player player) {
        resetPlayer(player);

        //TODO: add some sort of kit object or something that decides the kit which implemens filing inv
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, ItemStack.of(Material.DIAMOND_SWORD));

        inv.setHelmet(ItemStack.of(Material.DIAMOND_HELMET));
        inv.setChestplate(ItemStack.of(Material.DIAMOND_CHESTPLATE));
        inv.setLeggings(ItemStack.of(Material.DIAMOND_LEGGINGS));
        inv.setBoots(ItemStack.of(Material.DIAMOND_BOOTS));

        inv.setItem(8, ItemStack.of(Material.BREAD, 16));
    }

    private void changeIntoSpectator(Player player) {
        TournamentData data = (TournamentData) getOrCreatePlayerData(player.getUniqueId());
        data.setState(PlayerTournamentState.SPECTATOR);

        player.getInventory().clear();
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    private void resetPlayer(Player player) {
        player.clearActivePotionEffects();
        player.clearActiveItem();
        player.getInventory().clear();

        player.setHealth(20);
        player.setSaturation(4);
        player.setFoodLevel(20);
    }

    //endregion

    //region Arenas

    public void createWaitingArena() {
        int size = 10;

        World world = fest.world();
        Location origin = fest.origin().clone().add(-size / 2, -1, -size / 2);

        List<Material> possibleDecoBlocks = Tag.GLAZED_TERRACOTTA.getValues().stream().toList();

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    Block block = world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);

                    if (x != 0 && x != size - 1 &&
                            y != 0 && y != size - 1 &&
                            z != 0 && z != size - 1) {
                        blockTracker.setBlock(block, Material.AIR);
                        continue;
                    }

                    Material material = possibleDecoBlocks.get((int) (Math.random() * possibleDecoBlocks.size()));
                    blockTracker.setBlock(block, material);
                }
            }
        }
        blockTracker.save();
    }

    private static final int ARENA_WIDTH = 11;
    private static final int ARENA_LENGTH = 25;
    private static final int ARENA_HEIGHT = 7;

    private static final int ARENA_GAP = 2;
    private static final int ARENA_SPACING_X = ARENA_WIDTH + ARENA_GAP;
    private static final int ARENA_SPACING_Z = ARENA_LENGTH + ARENA_GAP;

    private CompletableFuture<Void> buildArenas() {
        Location origin = fest.origin().clone().add(100, 0, 0);

        int arenaCount = activeFaceOffs.size();

        if (arenaCount == 0) {
            updateArenaBuildingProgress();
            return CompletableFuture.completedFuture(null);
        }

        int columns = (int) Math.ceil(Math.sqrt(arenaCount));
        int rows = (int) Math.ceil((double) arenaCount / columns);

        List<CompletableFuture<Void>> arenaCompletions = new ArrayList<>(arenaCount);

        for (int i = 0; i < arenaCount; i++) {
            int row = i / columns;
            int column = i % columns;

            int columnsInRow = Math.min(columns, arenaCount - row * columns);

            int xOffset = column * ARENA_SPACING_X
                    - (columnsInRow - 1) * ARENA_SPACING_X / 2;

            int zOffset = row * ARENA_SPACING_Z
                    - (rows - 1) * ARENA_SPACING_Z / 2;

            Location arenaOrigin = origin.clone().add(xOffset, 0, zOffset);
            arenaCompletions.add(buildSingleArena(arenaOrigin));
        }

        updateArenaBuildingProgress();

        return CompletableFuture.allOf(arenaCompletions.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> buildSingleArena(Location origin) {
        World world = origin.getWorld();

        int minX = origin.getBlockX();
        int minZ = origin.getBlockZ();

        int maxX = minX + ARENA_WIDTH - 1;
        int maxZ = minZ + ARENA_LENGTH - 1;

        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        List<CompletableFuture<Chunk>> chunkRequests = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunkRequests.add(world.getChunkAtAsync(chunkX, chunkZ, true));
            }
        }

        CompletableFuture<Void> chunksLoaded = CompletableFuture.allOf(
                chunkRequests.toArray(CompletableFuture[]::new)
        );

        return chunksLoaded.thenRun(() -> {
            int xOffset = Math.floorDiv(ARENA_WIDTH, 2);
            arenas.add(new Arena(
                    origin.clone().add(xOffset, 1, ARENA_LENGTH * 0.8).toCenterLocation(),
                    origin.clone().add(xOffset, 1, ARENA_LENGTH * 0.2).toCenterLocation()
            ));

            for (int x = 0; x < ARENA_WIDTH; x++) {
                for (int z = 0; z < ARENA_LENGTH; z++) {
                    Location location = origin.clone().add(x, 0, z);
                    blockTracker.setBlock(location, Material.POLISHED_BLACKSTONE);

                    for (int y = 1; y < ARENA_HEIGHT; y++) {
                        boolean wall = x == 0 || x == ARENA_WIDTH - 1;
                        boolean end = z == 0 || z == ARENA_LENGTH - 1;
                        boolean roof = y == ARENA_HEIGHT - 1;

                        blockTracker.setBlock(origin.clone().add(x, y, z), wall || end || roof
                                ? Material.GLASS
                                : Material.AIR);
                    }
                }
            }
            blockTracker.save();
            updateArenaBuildingProgress();
        });
    }

    private void updateArenaBuildingProgress() {
        updateArenaBuildingBar(Component.text("Building arenas... ", NamedTextColor.GRAY)
                        .append(Component.text(arenas.size(), NamedTextColor.GOLD))
                        .append(Component.text(" / " + activeFaceOffs.size(), NamedTextColor.GRAY))
                , (float) arenas.size() / activeFaceOffs.size());
    }

    //endregion

    //region Event Handling

    @Override
    public void onPlayerDamage(EntityDamageEvent event, Player player) {
        TournamentData tournamentData = (TournamentData) getOrCreatePlayerData(player.getUniqueId());

        if (tournamentData.state() != PlayerTournamentState.BATTLE)
            event.setCancelled(true);
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event, Player player) {
        event.setCancelled(true);

        FaceOff faceOff = activeFaceOffs.stream()
                .filter(match -> match.player1.equals(player) || match.player2.equals(player))
                .findFirst().orElse(null);
        if (faceOff == null) return;

        finishFaceOff(faceOff);

        changeIntoSpectator(player);

        Player winner = faceOff.player1.equals(player) ? faceOff.player2 : faceOff.player1;
        TournamentData winnerData = (TournamentData) getOrCreatePlayerData(winner.getUniqueId());
        winnerData.setState(PlayerTournamentState.WAITING);

        player.sendTitlePart(TitlePart.TITLE, Component.text("Eliminated", NamedTextColor.RED));
        player.sendTitlePart(TitlePart.SUBTITLE, MiniMessage.miniMessage()
                .deserialize("<head:" + winner.getUniqueId() + "> <gold>" + winner.getName() + "</gold> " +
                        "<gray>won the duel"));
        player.playSound(player, Sound.ENTITY_BAT_DEATH, 1f, 0.25f);

        winner.sendTitlePart(TitlePart.TITLE, Component.empty());
        winner.sendTitlePart(TitlePart.SUBTITLE, Component.text("Duel won!", NamedTextColor.GREEN));
        winner.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

        winner.getWorld().spawn(winner.getEyeLocation(), Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.BLUE)
                    .withFade(Color.AQUA)
                    .withFlicker()
                    .build());
            firework.setFireworkMeta(meta);
            firework.setTicksToDetonate(1);
        });

        //TODO: create a variable for initial faceOffs per round, arena can not be trusted apart form the first round
        fest.participants().forEach(participant -> participant.sendMessage(
                Component.text(winner.getName(), NamedTextColor.GOLD)
                        .append(Component.text(" has won their duel (", NamedTextColor.GRAY))
                        .append(Component.text(arenas.size() - activeFaceOffs.size(), NamedTextColor.GREEN))
                        .append(Component.text("/" + arenas.size() + ")", NamedTextColor.GRAY))
        ));
    }

    //endregion

    @Override
    protected @NonNull FestPlayerData createPlayerData(UUID uuid) {
        return new TournamentData(uuid);
    }

    private record FaceOff(Player player1, Player player2) {
        public List<Player> players() {
            return List.of(player1, player2);
        }
    }

    private record Arena(Location player1Spawn, Location player2Spawn) {
    }
}
