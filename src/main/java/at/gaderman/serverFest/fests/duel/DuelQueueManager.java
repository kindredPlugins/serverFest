package at.gaderman.serverFest.fests.duel;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestManager;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class DuelQueueManager implements Listener {

    private static DuelQueueManager instance;

    public static DuelQueueManager getInstance() {
        if (instance == null) instance = new DuelQueueManager();
        return instance;
    }

    private final Queue<Duel> queue = new LinkedList<>();

    private DuelQueueManager() {
        Bukkit.getPluginManager().registerEvents(this, ServerFest.getPlugin());
    }

    // region API

    public void enqueue(Player player1, Player player2, @Nullable GameType gameType) {
        Duel duel = new Duel(player1, player2, gameType);

        synchronized (queue) {
            queue.add(duel);
        }

        for (Player player : new Player[]{player1, player2}) {
            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(Component.text("You are now placed in the queue for ", NamedTextColor.GRAY))
                    .append(label(gameType))
                    .appendSpace()
                    .append(cancelButton(duel)));

            startActionBarTask(duel, player);
        }
    }

    /**
     * Called when an arena for the given game type became available.
     * Takes the first waiting duel that accepts this game type (a duel without a game type accepts any).
     */
    public void presentArenaUpdate(GameType gameType) {
        Duel duel = null;

        synchronized (queue) {
            for (Duel candidate : queue) {
                if (candidate.gameType == null || candidate.gameType == gameType) {
                    duel = candidate;
                    break;
                }
            }
            if (duel != null) {
                queue.remove(duel);
            }
        }

        if (duel == null) return;

        if (!duel.closed.compareAndSet(false, true)) return;

        duel.found = true;
        duel.cancelTasks();

        Player challenger = duel.player1;
        Player opponent = duel.player2;

        if (!challenger.isOnline() || !opponent.isOnline()) {
            Player remaining = challenger.isOnline() ? challenger : opponent;
            Player leaver = remaining == challenger ? opponent : challenger;
            if (remaining.isOnline()) {
                remaining.sendMessage(ServerFest.CHAT_PREFIX
                        .append(Component.text(leaver.getName() + " left, you have been removed from the queue.", NamedTextColor.RED)));
                remaining.playSound(remaining, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
            return;
        }

        if(FestManager.getInstance().isPlayerParticipatingInAnyFest(challenger)
                || FestManager.getInstance().isPlayerParticipatingInAnyFest(opponent)){
            List.of(challenger, opponent).forEach(both -> {
                both.playSound(both.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1f, 0.5f);
                both.sendMessage(Component.text("Duel was cancelled due to participating in another fest", NamedTextColor.RED));
            });
            return;
        }

        Fest fest = new Fest(FestType.DUEL, gameType, new ArrayList<>(List.of(challenger.getUniqueId(), opponent.getUniqueId())));
        fest.start();
    }

    //endregion

    // region Listener

    /**
     * Safety net in addition to the scheduler check.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player quitter = event.getPlayer();
        List<Duel> affected;

        synchronized (queue) {
            affected = queue.stream()
                    .filter(d -> d.player1.equals(quitter) || d.player2.equals(quitter))
                    .toList();
        }

        for (Duel duel : affected) {
            closeDuel(duel, quitter);
        }
    }

    //endregion

    //region Helper

    private Component cancelButton(Duel duel) {
        return Component.text("[Cancel]", NamedTextColor.RED)
                .clickEvent(ClickEvent.callback(_ -> closeDuel(duel, null),
                        ClickCallback.Options.builder().uses(1).build()));
    }

    private void startActionBarTask(Duel duel, Player player) {
        Component actionBar = Component.text("Queuing for duel ")
                .append(label(duel.gameType).colorIfAbsent(NamedTextColor.YELLOW));

        ScheduledTask task = player.getScheduler().runAtFixedRate(ServerFest.getPlugin(), scheduled -> {
            if (duel.closed.get() || duel.found) {
                scheduled.cancel();
                return;
            }

            if (!duel.player1.isOnline() || !duel.player2.isOnline()) {
                Player leaver = !duel.player1.isOnline() ? duel.player1 : duel.player2;
                closeDuel(duel, leaver);
                scheduled.cancel();
                return;
            }

            player.sendActionBar(actionBar);
        }, () -> {
            closeDuel(duel, player);
        }, 1L, 20L);

        if (task != null) {
            duel.tasks.add(task);
            if (duel.closed.get()) task.cancel();
        }
    }

    /**
     * Ends a queued duel exactly once.
     *
     * @param leaver the player who left, or null if the duel was cancelled manually via the [Cancel] button
     */
    private void closeDuel(Duel duel, @Nullable Player leaver) {
        if (!duel.closed.compareAndSet(false, true)) return; // already handled

        synchronized (queue) {
            queue.remove(duel);
        }
        duel.cancelTasks();

        if (leaver == null) {
            Component message = ServerFest.CHAT_PREFIX.append(
                    Component.text("The duel queue was cancelled.", NamedTextColor.RED));
            for (Player player : new Player[]{duel.player1, duel.player2}) {
                if (player.isOnline()) {
                    player.sendMessage(message);
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.1f);
                }
            }
        } else {
            Player other = leaver.equals(duel.player1) ? duel.player2 : duel.player1;
            if (other.isOnline()) {
                other.sendMessage(ServerFest.CHAT_PREFIX.append(
                        Component.text(leaver.getName() + " left, you have been removed from the queue.",
                                NamedTextColor.RED)));
                other.playSound(other, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
        }
    }

    private static Component label(@Nullable GameType gameType) {
        return gameType == null ? Component.text("any") : gameType.definition().iconName();
    }

    //endregion

    private static class Duel {
        final Player player1;
        final Player player2;
        @Nullable
        final GameType gameType;
        volatile boolean found;

        /**
         * Guards against double handling (cancel click + quit + scheduler can all race).
         */
        final AtomicBoolean closed = new AtomicBoolean(false);
        final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();

        Duel(Player player1, Player player2, @Nullable GameType gameType) {
            this.player1 = player1;
            this.player2 = player2;
            this.gameType = gameType;
        }

        void cancelTasks() {
            for (ScheduledTask task : tasks) task.cancel();
            tasks.clear();
        }
    }
}
