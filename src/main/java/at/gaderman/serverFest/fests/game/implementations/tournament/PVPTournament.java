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
import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerDropItemTrigger;
import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerInventoryClickTrigger;
import at.gaderman.serverFest.fests.game.triggers.inventory.PlayerPickupItemTrigger;
import at.gaderman.serverFest.fests.game.triggers.prefabs.NoBlockManipulationPrefab;
import at.gaderman.serverFest.fests.items.FestItemProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PVPTournament extends FestGame<ArenaLayout> implements NoBlockManipulationPrefab,
        PlayerDamageTrigger, PlayerDeathTrigger, PlayerPickupItemTrigger, PlayerDropItemTrigger, PlayerInventoryClickTrigger {

    private final List<FaceOff> activeFaceOffs = new ArrayList<>();
    private final List<Arena> arenas = new ArrayList<>();

    private int arenasUsedForRound;
    private boolean isBattlePhase;

    public PVPTournament(Fest fest, ArenaInstance arenaInstance) {
        super(fest, arenaInstance);
    }

    @Override
    public @NotNull GameType gameType() {
        return GameType.PVP_TOURNAMENT;
    }

    @Override
    public Location determineOrigin() {
        Location origin = super.determineOrigin();
        origin.setY(300);
        return origin;
    }

    @Override
    public CompletableFuture<Void> setUpWorld() {
        bonusFaceOffs = fest.participants().size() > 1 ? 0 : 1;
        groupIntoFaceOffs();
        return createWaitingArena().thenCompose(ignored -> buildArenas());
    }

    @Override
    protected Component startTitle() {
        return Component.text("Waiting...", NamedTextColor.GOLD);
    }

    @Override
    protected void onCountdownComplete() {
        startFaceOffs();
        fest.participants().forEach(this::addActionBarSchedule);
    }

    @Override
    protected void onStop() {

    }

    @Override
    protected @NonNull CompletableFuture<Void> onReset() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected Sound winnerCelebrationSound() {
        return Sound.MUSIC_DISC_CREATOR;
    }

    @Override
    protected void configureWinnerFirework(Firework firework) {
        FireworkMeta meta = firework.getFireworkMeta();
        meta.clearEffects();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.BLUE)
                .withFade(Color.FUCHSIA)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withFlicker()
                .withTrail()
                .build());
        firework.setFireworkMeta(meta);
    }

    //region Organization

    private final Map<FaceOff, Arena> arenaPerDuelMap = new HashMap<>();

    //TODO: for testing purposes to be able to play alone, remote later
    private int bonusFaceOffs;

    private void groupIntoFaceOffs() {
        activeFaceOffs.clear();

        List<Player> participants = fest.participants().stream()
                .filter(player -> ((TournamentData) getOrCreatePlayerData(player.getUniqueId())).state() == PlayerTournamentState.WAITING)
                .collect(Collectors.toList());
        Collections.shuffle(participants);

        for (int i = 0; i < participants.size() / 2; i++) {
            int index = i * 2;

            activeFaceOffs.add(new FaceOff(participants.get(index), participants.get(index + 1)));
        }

        while (bonusFaceOffs > 0) {
            activeFaceOffs.add(new FaceOff(participants.getFirst(), participants.getFirst()));
            bonusFaceOffs--;
        }

        arenasUsedForRound = activeFaceOffs.size();
    }

    private boolean checkIfFaceOffWinnerIsAlreadyDecided() {
        if (activeFaceOffs.isEmpty()) {
            List<Player> winners = fest.participants().stream().filter(participant -> {
                TournamentData tournamentData = (TournamentData) getOrCreatePlayerData(participant.getUniqueId());
                return tournamentData.state() == PlayerTournamentState.WAITING;
            }).toList();

            if (winners.isEmpty()) {
                declareWinner(Collections.emptyList());
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

        isBattlePhase = true;
        arenaPerDuelMap.clear();

        for (int i = 0; i < activeFaceOffs.size(); i++) {
            FaceOff faceOff = activeFaceOffs.get(i);
            Arena arena = arenas.get(i);
            arenaPerDuelMap.put(faceOff, arena);

            faceOff.player1.teleport(arena.player1Spawn);
            faceOff.player2.teleport(arena.player2Spawn);

            ((TournamentData) getOrCreatePlayerData(faceOff.player1.getUniqueId())).setState(PlayerTournamentState.BATTLE);
            ((TournamentData) getOrCreatePlayerData(faceOff.player2.getUniqueId())).setState(PlayerTournamentState.BATTLE);

            faceOff.players().forEach(player -> {
                player.resetTitle();
                player.sendTitlePart(TitlePart.TITLE, Component.text("Fight!", NamedTextColor.RED));
                player.sendActionBar(Component.empty());
                player.playSound(player, Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1.3f);

                quipWithKit(player);
            });
        }

        fest.participants().stream().filter(player ->
                        ((TournamentData) getOrCreatePlayerData(player.getUniqueId())).state() == PlayerTournamentState.WAITING)
                .forEach(bye -> {
                    bye.getInventory().setItem(0, FestItemProvider.SPEC_COMPASS);
                    bye.sendMessage(Component.text("You are skipping this round but will return in the next battle round"));
                });

        startBattlePhase();
    }

    /**
     * Handles removing an active faceOff in a way where it will automatically trigger the next round if it was the last to finish
     */
    private void finishFaceOff(FaceOff faceOff) {
        activeFaceOffs.remove(faceOff);

        faceOff.activePlayers(fest).forEach(both -> {
            both.getScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                if (!isRunning())
                    return;

                resetPlayer(both);
                both.getInventory().setItem(0, FestItemProvider.SPEC_COMPASS);
                both.teleportAsync(fest.origin().toCenterLocation());
            }, null, 20);
        });

        if (activeFaceOffs.isEmpty()) {
            int initialDelay = 30;
            int delay = 100;

            Bukkit.getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                isBattlePhase = false;

                if (!isRunning())
                    return;

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
                if (activeFaceOffs.isEmpty() || !isRunning())
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

            List<Player> engagedPlayers = activeFaceOffs.stream()
                    .flatMap(faceOff -> faceOff.players().stream())
                    .toList();

            if (remainingBattleTicks == 200)
                engagedPlayers.forEach(player -> player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.35f));

            if (remainingBattleTicks <= 200 && remainingBattleTicks % 20 == 0) {
                engagedPlayers.forEach(player -> {
                    player.sendActionBar(Component.text("Battle ending in ", NamedTextColor.GRAY)
                            .append(Component.text(Math.floorDiv(remainingBattleTicks, 20), NamedTextColor.RED))
                            .append(Component.text(" second(s)", NamedTextColor.GRAY))
                    );
                });
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


    @Override
    public void onLeave(Player player) {
        super.onLeave(player);

        if (!isRunning())
            return;

        FaceOff faceOff = faceOffOfPlayer(player);
        if (faceOff == null)
            return;

        if (isBattlePhase) {
            Player winner = player.equals(faceOff.player1) ? faceOff.player2 : faceOff.player1;
            finishFaceOff(faceOff);
            playWinDuel(winner);
        } else {
            groupIntoFaceOffs();
        }
    }

    private void addActionBarSchedule(Player player) {
        TournamentData playerData = (TournamentData) getOrCreatePlayerData(player.getUniqueId());

        player.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            if (!isRunning()) {
                task.cancel();
                return;
            }

            PlayerTournamentState state = playerData.state();

            if (state == PlayerTournamentState.BATTLE)
                return;

            Component display = state == PlayerTournamentState.SPECTATOR
                    ? Component.text("Eliminated ", NamedTextColor.RED).append(Component.text("✦ ", NamedTextColor.DARK_GRAY))
                    : Component.empty();

            if (isBattlePhase)
                display = display.append(Component.text("Waiting for ", NamedTextColor.GRAY))
                        .append(Component.text("duels ", NamedTextColor.BLUE))
                        .append(Component.text("to finish (", NamedTextColor.GRAY))
                        .append(Component.text(arenasUsedForRound - activeFaceOffs.size(), NamedTextColor.GOLD))
                        .append(Component.text("/" + arenasUsedForRound + ")", NamedTextColor.GRAY));

            else
                display = display.append(Component.text("Waiting for ", NamedTextColor.GRAY))
                        .append(Component.text("next round", NamedTextColor.GOLD));

            player.sendActionBar(display);
        }, null, 5, 5);
    }

    private void quipWithKit(Player player) {
        resetPlayer(player);

        //TODO: add some sort of kit object or something that decides the kit which implements filing inv
        PlayerInventory inv = player.getInventory();
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 2);
        inv.setItem(0, sword);

        inv.setHelmet(ItemStack.of(Material.DIAMOND_HELMET));
        inv.setChestplate(ItemStack.of(Material.DIAMOND_CHESTPLATE));
        inv.setLeggings(ItemStack.of(Material.DIAMOND_LEGGINGS));
        inv.setBoots(ItemStack.of(Material.DIAMOND_BOOTS));
    }

    private void playWinDuel(Player winner) {
        ((TournamentData) getOrCreatePlayerData(winner.getUniqueId())).setState(PlayerTournamentState.WAITING);

        winner.sendTitlePart(TitlePart.TITLE, Component.empty());
        winner.sendTitlePart(TitlePart.SUBTITLE, Component.text("Duel won!", NamedTextColor.GREEN));
        winner.playSound(winner, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

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

        fest.participants().forEach(participant -> participant.sendMessage(
                Component.text(winner.getName(), NamedTextColor.GOLD)
                        .append(Component.text(" has won their duel (", NamedTextColor.GRAY))
                        .append(Component.text(arenasUsedForRound - activeFaceOffs.size(), NamedTextColor.GREEN))
                        .append(Component.text("/" + arenas.size() + ")", NamedTextColor.GRAY))
        ));
    }

    private void changeIntoSpectator(Player player) {
        TournamentData data = (TournamentData) getOrCreatePlayerData(player.getUniqueId());
        data.setState(PlayerTournamentState.SPECTATOR);

        player.getInventory().clear();
        player.getInventory().setItem(0, FestItemProvider.SPEC_COMPASS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    private void resetPlayer(Player player) {
        player.clearActivePotionEffects();
        player.clearActiveItem();
        player.getInventory().clear();

        player.setHealth(20);
        player.setSaturation(4);
        player.setFoodLevel(20);

        if (stateOf(player) == PlayerTournamentState.SPECTATOR)
            changeIntoSpectator(player);
    }

    //endregion

    //region exposure

    public List<Player> battlingPlayers() {
        return participantsOfState(PlayerTournamentState.BATTLE);
    }

    /**
     * Requests a spectator teleport from visitor to toVisit and if successful, teleports the player
     * to the spectator area of the arena. Called from {@link ArenaSpectateGUI} to connect GUI
     * with the actual game.
     */
    public boolean requestArenaVisitFor(Player visitor, Player toVisit) {
        if (!fest.participantUUIDs().contains(visitor.getUniqueId())
                || !fest.participantUUIDs().contains(toVisit.getUniqueId())) {
            return false;
        }

        FaceOff target = activeFaceOffs.stream()
                .filter(faceOff -> faceOff.players().contains(toVisit))
                .findFirst().orElse(null);
        if (target == null)
            return false;

        visitor.playSound(visitor, Sound.ENTITY_ENDERMAN_TELEPORT, 30f, 1f);
        visitor.teleportAsync(arenaPerDuelMap.get(target).spectatorPoint);
        return true;
    }

    //endregion

    //region Arenas

    private CompletableFuture<Void> createWaitingArena() {
        World world = fest.world();
        Location origin = fest.origin().clone().add(-HUB_WIDTH / 2, -1, -HUB_LENGTH / 2);
        int minChunkX = Math.floorDiv(origin.getBlockX(), 16);
        int maxChunkX = Math.floorDiv(origin.getBlockX() + HUB_WIDTH - 1, 16);
        int minChunkZ = Math.floorDiv(origin.getBlockZ(), 16);
        int maxChunkZ = Math.floorDiv(origin.getBlockZ() + HUB_LENGTH - 1, 16);
        List<CompletableFuture<Chunk>> chunkRequests = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunkRequests.add(world.getChunkAtAsync(chunkX, chunkZ, true));
            }
        }

        return CompletableFuture.allOf(chunkRequests.toArray(CompletableFuture[]::new)).thenRun(() -> {
            for (int x = 0; x < HUB_WIDTH; x++) {
                for (int y = 0; y < HUB_HEIGHT; y++) {
                    for (int z = 0; z < HUB_LENGTH; z++) {
                        Block block = world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y,
                                origin.getBlockZ() + z);
                        boolean wall = x == 0 || x == HUB_WIDTH - 1 || z == 0 || z == HUB_LENGTH - 1;
                        Material material;

                        if (y == 0) {
                            boolean perimeter = x == 0 || x == HUB_WIDTH - 1
                                    || z == 0 || z == HUB_LENGTH - 1;
                            material = perimeter ? Material.DEEPSLATE_TILES : Material.GLASS;
                        } else if (y == HUB_HEIGHT - 1) {
                            boolean ceilingLight = (x == HUB_WIDTH / 2 - 1 || x == HUB_WIDTH / 2)
                                    && (z == HUB_LENGTH / 2 - 1 || z == HUB_LENGTH / 2);
                            material = ceilingLight ? Material.SEA_LANTERN : Material.DEEPSLATE_TILES;
                        } else if (!wall) {
                            material = Material.AIR;
                        } else {
                            boolean corner = (x == 0 || x == HUB_WIDTH - 1)
                                    && (z == 0 || z == HUB_LENGTH - 1);
                            int wallPosition = x == 0 || x == HUB_WIDTH - 1 ? z : x;
                            int wallLength = x == 0 || x == HUB_WIDTH - 1 ? HUB_LENGTH : HUB_WIDTH;
                            boolean glassOpening = y >= 2 && y <= HUB_HEIGHT - 3
                                    && wallPosition >= 2 && wallPosition <= wallLength - 3;
                            material = glassOpening && !corner ? Material.GLASS : Material.DEEPSLATE_TILES;
                        }

                        blockTracker.setBlock(block, material);
                    }
                }
            }
            blockTracker.save();
        });
    }

    private static final int ARENA_WIDTH = 11;
    private static final int ARENA_LENGTH = 25;
    private static final int ARENA_HEIGHT = 7;
    private static final int GALLERY_WIDTH = 5;
    private static final int HUB_WIDTH = ARENA_WIDTH + GALLERY_WIDTH;
    private static final int HUB_LENGTH = ARENA_LENGTH;
    private static final int HUB_HEIGHT = 10;
    private static final int ARENA_VERTICAL_SEPARATION = 12;

    private static final int ARENA_GAP = 10;
    private static final int ARENA_SPACING_X = ARENA_WIDTH + ARENA_GAP;
    private static final int ARENA_SPACING_Z = ARENA_LENGTH + ARENA_GAP;

    private CompletableFuture<Void> buildArenas() {
        Location center = fest.origin();
        int hubFloorY = center.getBlockY() - 1;
        int arenaFloorY = hubFloorY - ARENA_VERTICAL_SEPARATION;

        int arenaCount = activeFaceOffs.size();

        if (arenaCount == 0) {
            updateArenaBuildingProgress();
            return CompletableFuture.completedFuture(null);
        }

        int columns = (int) Math.ceil(Math.sqrt(arenaCount));
        int rows = (int) Math.ceil((double) arenaCount / columns);
        int arrayDepth = rows * ARENA_LENGTH + (rows - 1) * ARENA_GAP;
        int firstRowZ = center.getBlockZ() - arrayDepth / 2;

        List<CompletableFuture<Void>> arenaCompletions = new ArrayList<>(arenaCount);

        for (int i = 0; i < arenaCount; i++) {
            int row = i / columns;
            int column = i % columns;

            int columnsInRow = Math.min(columns, arenaCount - row * columns);
            int rowWidth = columnsInRow * (ARENA_WIDTH + GALLERY_WIDTH)
                    + (columnsInRow - 1) * (ARENA_SPACING_X - (ARENA_WIDTH + GALLERY_WIDTH));
            int firstArenaX = center.getBlockX() - rowWidth / 2;
            int arenaX = firstArenaX + column * ARENA_SPACING_X;
            int arenaZ = firstRowZ + row * ARENA_SPACING_Z;

            Location arenaOrigin = new Location(center.getWorld(), arenaX, arenaFloorY, arenaZ);
            arenaCompletions.add(buildSingleArena(arenaOrigin));
        }

        updateArenaBuildingProgress();

        return CompletableFuture.allOf(arenaCompletions.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> buildSingleArena(Location origin) {
        World world = origin.getWorld();

        int minX = origin.getBlockX();
        int minZ = origin.getBlockZ();

        int maxX = minX + ARENA_WIDTH + GALLERY_WIDTH - 1;
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
            Location spectatorPoint = origin.clone()
                    .add(ARENA_WIDTH + GALLERY_WIDTH / 2, 1, ARENA_LENGTH * 0.5)
                    .toCenterLocation();
            spectatorPoint.setDirection(new Vector(-1, 0, 0));

            Location spawnOne = origin.clone().add(xOffset, 1, ARENA_LENGTH * 0.8).toCenterLocation();
            Location spawnTwo = origin.clone().add(xOffset, 1, ARENA_LENGTH * 0.2).toCenterLocation();
            spawnOne.setYaw(180);

            arenas.add(new Arena(spawnOne, spawnTwo, spectatorPoint));

            for (int x = 0; x < ARENA_WIDTH; x++) {
                for (int z = 0; z < ARENA_LENGTH; z++) {
                    Location location = origin.clone().add(x, 0, z);
                    Material floor = getFloor(x, z, xOffset);
                    blockTracker.setBlock(location, floor);

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

            for (int x = 0; x < GALLERY_WIDTH; x++) {
                for (int z = 0; z < ARENA_LENGTH; z++) {
                    boolean edge = x == 0 || x == GALLERY_WIDTH - 1
                            || z == 0 || z == ARENA_LENGTH - 1;
                    Material floor = edge ? Material.DEEPSLATE_TILES : Material.POLISHED_BLACKSTONE;
                    if (x == GALLERY_WIDTH / 2 && (z == 6 || z == ARENA_LENGTH - 1 - 6))
                        floor = Material.SEA_LANTERN;

                    blockTracker.setBlock(origin.clone().add(ARENA_WIDTH + x, 0, z), floor);

                    if (x == GALLERY_WIDTH - 1 || z == 0 || z == ARENA_LENGTH - 1) {
                        for (int y = 1; y <= 2; y++)
                            blockTracker.setBlock(origin.clone().add(ARENA_WIDTH + x, y, z),
                                    Material.GLASS_PANE);
                    }
                }
            }

            blockTracker.save();
            updateArenaBuildingProgress();
        });
    }

    private static @NonNull Material getFloor(int x, int z, int xOffset) {
        boolean perimeter = x == 0 || x == ARENA_WIDTH - 1
                || z == 0 || z == ARENA_LENGTH - 1;
        boolean spawnAccent = x >= xOffset - 1 && x <= xOffset + 1
                && (Math.abs(z - (int) (ARENA_LENGTH * 0.8)) <= 1
                || Math.abs(z - (int) (ARENA_LENGTH * 0.2)) <= 1);
        return perimeter
                ? Material.DEEPSLATE_TILES
                : spawnAccent ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE;
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

        player.sendTitlePart(TitlePart.TITLE, Component.text("Eliminated", NamedTextColor.RED));

        Component prefix = ServerFest.supportsChatObjects
                ? MiniMessage.miniMessage().deserialize("<head:" + winner.getUniqueId() + "> ")
                : Component.empty();
        player.sendTitlePart(TitlePart.SUBTITLE, prefix
                .append(Component.text(winner.getName(), NamedTextColor.GOLD))
                .append(Component.text(" won the duel", NamedTextColor.GRAY))
        );
        player.playSound(player, Sound.ENTITY_BAT_DEATH, 1f, 0.25f);

        playWinDuel(winner);
    }

    @Override
    public void onInteract(PlayerInteractEvent event, Player player) {
        ItemStack item = event.getItem();

        if (!event.getAction().isRightClick())
            return;

        if (item == null || !item.equals(FestItemProvider.SPEC_COMPASS))
            return;

        new ArenaSpectateGUI(this, activeFaceOffs).openInventory(player);
        player.playSound(player, Sound.BLOCK_CHEST_OPEN, 1f, 1.45f);
    }

    @Override
    public void onPlayerPickupItem(EntityPickupItemEvent event, Player player, Item item) {
        if (stateOf(player) == PlayerTournamentState.BATTLE)
            return;

        event.setCancelled(true);
    }

    @Override
    public void onPlayerDropItem(PlayerDropItemEvent event, Player player, Item item) {
        if (stateOf(player) == PlayerTournamentState.BATTLE)
            return;

        event.setCancelled(true);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event, Player player) {
        if (stateOf(player) == PlayerTournamentState.BATTLE)
            return;

        event.setCancelled(true);
    }

    //endregion

    //region Helpers

    private PlayerTournamentState stateOf(Player player) {
        return ((TournamentData) getOrCreatePlayerData(player.getUniqueId())).state();
    }

    private List<Player> participantsOfState(PlayerTournamentState state) {
        return fest.participants().stream().filter(player -> stateOf(player) == state)
                .toList();
    }

    private @Nullable FaceOff faceOffOfPlayer(Player player) {
        return activeFaceOffs.stream().filter(faceOff -> faceOff.players().contains(player)).findFirst().orElse(null);
    }

    //endregion

    @Override
    protected @NonNull FestPlayerData createPlayerData(UUID uuid) {
        return new TournamentData(uuid);
    }

    public record FaceOff(Player player1, Player player2) {
        public List<Player> players() {
            return List.of(player1, player2);
        }

        public List<Player> activePlayers(Fest fest) {
            return players().stream().filter(player -> fest.participantUUIDs().contains(player.getUniqueId())).toList();
        }
    }

    public record Arena(Location player1Spawn, Location player2Spawn, Location spectatorPoint) {
    }
}
