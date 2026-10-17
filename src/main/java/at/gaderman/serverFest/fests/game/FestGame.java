package at.gaderman.serverFest.fests.game;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.arenas.ArenaBlockTracker;
import at.gaderman.serverFest.fests.arenas.ArenaInstance;
import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.game.definition.GameDefinition;
import at.gaderman.serverFest.fests.game.interfaces.FestPlayerData;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class FestGame<T extends ArenaLayout> {

    protected final Fest fest;
    protected final ArenaInstance arenaInstance;
    protected final T arenaLayout;

    private boolean running;
    private final List<Player> winners = new ArrayList<>();
    private BossBar arenaBuildingBar;
    protected final ArenaBlockTracker blockTracker;

    private final Map<UUID, FestPlayerData> playerDataMap = new HashMap<>();

    public FestGame(Fest fest, ArenaInstance arenaInstance) {
        this.fest = fest;
        this.arenaInstance = arenaInstance;
        this.arenaLayout = (T) arenaInstance.arenaLayout();
        this.blockTracker = new ArenaBlockTracker(arenaInstance);
    }

    /**
     * Returns the corresponding {@link GameType} linked to this fest instance, mostly used for obtaining the {@link GameDefinition}
     */
    public abstract @NotNull GameType gameType();

    public Location determineOrigin() {
        return arenaLayout.origin();
    }

    /**
     * Called to setup the game specific world, the world itself is already created inside fest, this just initializes it for the game, like adding race tracks or arenas
     *
     * @return a runnable callback when world setup has been completed
     */
    public abstract CompletableFuture<Void> setUpWorld();

    /**
     * Called when fest participants have been teleported to the fest world, usually used to create a starting are for them to gather
     */
    public void onPlayersJoinedWorld() {
        updateArenaBuildingBar(Component.text("Setting up arena..."), 0.0f);
    }

    /**
     * Start the game, setting its status to running and starting the shared countdown.
     */
    public final void start() {
        running = true;
        if (arenaBuildingBar != null) {
            List<Audience> viewers = new ArrayList<>();
            arenaBuildingBar.viewers().forEach(viewer -> viewers.add((Audience) viewer));
            viewers.forEach(arenaBuildingBar::removeViewer);
        }

        onStart();
    }

    /**
     * Called after the game countdown completes.
     */
    protected final void onStart() {
        final int[] timeLeft = {countdownSeconds()};
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            timeLeft[0]--;

            if (timeLeft[0] <= 5)
                sendCountdownTitle(timeLeft[0]);

            if (timeLeft[0] <= 0) {
                task.cancel();

                fest.participants().forEach(participant -> {
                    participant.setWorldBorder(fest.world().getWorldBorder());
                    participant.playSound(participant, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                    participant.sendTitlePart(TitlePart.TITLE, startTitle());
                });
                onCountdownComplete();
            }
        }, 20, 20);
    }

    protected int countdownSeconds() {
        return 10;
    }

    protected Component startTitle() {
        return Component.text("Race!", NamedTextColor.GOLD);
    }

    protected void onCountdownComplete() {
    }

    /**
     * Called by the underlying fest game when it declares one or multiple winner, will then trigger {@link #onWinner(List)}
     */
    protected final void declareWinner(List<Player> winners) {
        this.winners.addAll(winners);
        running = false;
        onWinner(winners);
        afterWinner(winners);
    }

    /**
     * Announces the winner and schedules the game to stop after the announcement.
     * If the list is empty it will count as a draw
     */
    protected final void onWinner(List<Player> winners) {
        Player singleWinner = winners.isEmpty() ? null : winners.getFirst();
        Component subtitle = winnerAnnouncementSubtitle(singleWinner);

        fest.participants().forEach(participant -> {
            boolean isWinner = winners.contains(participant);
            participant.resetTitle();

            if (isWinner) {
                participant.sendTitlePart(TitlePart.TITLE, winnerAnnouncementTitle());
                participant.getWorld().playSound(participant, winnerCelebrationSound(), 1f, 1.3f);

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
                    configureWinnerFirework(firework);
                });
            } else {
                participant.sendTitlePart(TitlePart.TITLE, nonWinnerAnnouncementTitle(singleWinner));
                participant.playSound(participant, Sound.ENTITY_WITHER_DEATH, 1f, 1.3f);
            }

            participant.sendTitlePart(TitlePart.SUBTITLE, subtitle);
        });

        Bukkit.getServer().getGlobalRegionScheduler().runDelayed(ServerFest.getPlugin(), _ -> stop(), 200);
    }

    protected Component winnerAnnouncementTitle() {
        return Component.text("Winner!", NamedTextColor.GOLD);
    }

    protected Component nonWinnerAnnouncementTitle(@Nullable Player winner) {
        if (winner == null)
            return Component.text("Draw", NamedTextColor.BLUE);

        return winner.displayName().color(NamedTextColor.AQUA)
                .append(Component.text(" has won!", NamedTextColor.GREEN));
    }

    protected Component winnerAnnouncementSubtitle(@Nullable Player winner) {
        if (winner == null) {
            return Component.text("No winner", NamedTextColor.GOLD);
        }
        return MiniMessage.miniMessage().deserialize("<head:" + winner.getUniqueId() + "> ")
                .append(winner.name().color(NamedTextColor.GOLD))
                .append(Component.text(" has won!", NamedTextColor.GREEN));
    }

    protected Sound winnerCelebrationSound() {
        return Sound.MUSIC_DISC_PIGSTEP;
    }

    protected void configureWinnerFirework(Firework firework) {
    }

    /**
     * Game-specific winner side effects run after the shared announcement has been sent.
     */
    protected void afterWinner(List<Player> winners) {
    }

    protected final void sendCountdownTitle(long timeLeft) {
        fest.participants().forEach(participant -> {
            participant.playSound(participant, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            participant.sendTitlePart(TitlePart.TITLE, Component.text(timeLeft, NamedTextColor.GREEN));
        });
    }

    /**
     * Stop the game either forcefully or because the underlying fest has completed
     */
    public void stop() {
        running = false;
        reset();
        onStop();

        fest.participants().forEach(participant -> {
            FestPlayerData festPlayerData = playerDataMap.getOrDefault(participant.getUniqueId(), null);
            if (festPlayerData != null)
                festPlayerData.reset();
        });
        fest.stop();
        //TODO: allow winner to pick reward
    }

    protected abstract void onStop();

    private void reset() {
        arenaInstance.markAsReset();
        blockTracker.restore().thenRun(() ->
                onReset().thenRun(arenaInstance::resetToReady)
        );
    }

    /**
     * The underlying reset method of each fest game, this will reset the arena so it can then be used for preceding games as well
     */
    protected abstract @NotNull CompletableFuture<Void> onReset();

    /**
     * Gets or creates player data for this fest for the given UUID
     *
     * @return A fresh FestPlayerData if none existed or the ongoing data of the player participant
     */
    public FestPlayerData getOrCreatePlayerData(UUID uuid) {
        if (playerDataMap.containsKey(uuid))
            return playerDataMap.get(uuid);

        FestPlayerData playerData = createPlayerData(uuid);
        playerDataMap.put(uuid, playerData);
        return playerData;
    }

    protected abstract @NonNull FestPlayerData createPlayerData(UUID uuid);

    public boolean isRunning() {
        return running;
    }

    /**
     * Further handle a player leaving the fest. This can be both from either quitting Minecraft completely
     * or simply leaving the fest via /fest quit.
     * Some fests need to adjust when a player leaves, like in a duel.
     * Will also trigger an automatic win if all players leave except the final one.
     */
    public void onLeave(Player player){
        getOrCreatePlayerData(player.getUniqueId()).reset();
    }

    //region Helper methods

    protected void updateArenaBuildingBar(Component text, float progress) {
        if (arenaBuildingBar == null) {
            arenaBuildingBar = BossBar.bossBar(text, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

            fest.participants().forEach(participant -> arenaBuildingBar.addViewer(participant));
        }

        arenaBuildingBar.name(text);
        arenaBuildingBar.progress(progress);
    }

    protected void loadChunks(World world, Set<ChunkPos> chunks, Consumer<Set<Chunk>> finished) {
        if (chunks.isEmpty()) {
            finished.accept(Collections.emptySet());
            return;
        }

        Set<Chunk> resolvedChunks = new LinkedHashSet<>();
        AtomicInteger remaining = new AtomicInteger(chunks.size());

        for (ChunkPos chunk : chunks) {
            world.getChunkAtAsync(chunk.x(), chunk.z(), true, resolved -> {
                resolvedChunks.add(resolved);

                updateArenaBuildingBar(Component.text("Loading chunks ", NamedTextColor.YELLOW)
                                .append(Component.text(chunks.size() - remaining.get(), NamedTextColor.GOLD))
                                .append(Component.text(" / ", NamedTextColor.GRAY))
                                .append(Component.text(chunks.size(), NamedTextColor.DARK_GRAY))
                        , (float) (chunks.size() - remaining.get()) / chunks.size());

                if (remaining.decrementAndGet() == 0)
                    finished.accept(resolvedChunks);
            });
        }
    }

    //endregion

    public record ChunkPos(int x, int z) {
    }
}
