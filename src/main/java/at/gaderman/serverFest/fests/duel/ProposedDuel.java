package at.gaderman.serverFest.fests.duel;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.game.GameType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the state when a player proposes a fest duel, in this state they other party may not have accepted yet or
 * they are still deciding details about the duel like a bet or the game to play.
 */
public class ProposedDuel {

    private static final int REQUEST_TIMEOUT = 20 * 20;

    private static final List<ProposedDuel> activeProposals = new ArrayList<>();

    private final Player challenger;
    private final Player opponent;
    private int remainingTime;
    private boolean accepted;
    private @Nullable GameType gameType;

    private ProposedDuel(Player challenger, Player opponent) {
        this.challenger = challenger;
        this.opponent = opponent;
        this.remainingTime = REQUEST_TIMEOUT;

        activeProposals.add(this);

        sendDuelRequest();
    }

    public static ProposedDuel propose(Player challenger, Player opponent) {
        return new ProposedDuel(challenger, opponent);
    }

    private void sendDuelRequest() {
        challenger.sendMessage(ServerFest.CHAT_PREFIX.append(
                Component.text("Sent a duel request to ", NamedTextColor.GRAY)
                        .append(Component.text(opponent.getName(), NamedTextColor.GOLD))
        ));
        challenger.playSound(challenger, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        opponent.sendMessage(ServerFest.CHAT_PREFIX.append(
                Component.text(challenger.getName(), NamedTextColor.GOLD)
                        .append(Component.text(" wants to challenge you to a duel ", NamedTextColor.GRAY))
                        .append(Component.text("[Accept] ", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("fest duel accept " + challenger.getName())))
                        .append(Component.text("[Decline] ", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("fest duel decline " + challenger.getName())))
        ));
        opponent.playSound(opponent, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.2f);

        scheduleTimeout();
    }

    private void scheduleTimeout() {
        opponent.getScheduler().runAtFixedRate(ServerFest.getPlugin(), task -> {
            if (accepted || !activeProposals.contains(this)) {
                task.cancel();
                return;
            }

            if (!challenger.isValid() || !challenger.isOnline()) {
                opponent.sendMessage(ServerFest.CHAT_PREFIX.append(
                        Component.text("Duel request from ", NamedTextColor.RED)
                                .append(Component.text(challenger.getName(), NamedTextColor.GOLD))
                                .append(Component.text(" expired since they left the server", NamedTextColor.RED))
                ));
                opponent.playSound(challenger, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);

                task.cancel();
                return;
            }

            if (remainingTime <= 0) {
                challenger.sendMessage(ServerFest.CHAT_PREFIX.append(
                        Component.text("Duel request to ", NamedTextColor.RED)
                                .append(Component.text(opponent.getName(), NamedTextColor.GOLD))
                                .append(Component.text(" expired", NamedTextColor.RED))
                ));
                challenger.playSound(challenger, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);

                opponent.sendMessage(ServerFest.CHAT_PREFIX.append(
                        Component.text("Duel request from ", NamedTextColor.RED)
                                .append(Component.text(challenger.getName(), NamedTextColor.GOLD))
                                .append(Component.text(" expired", NamedTextColor.RED))
                ));
                opponent.playSound(challenger, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);

                activeProposals.remove(this);
                task.cancel();
                return;
            }

            remainingTime--;
        }, () -> {
            challenger.sendMessage(ServerFest.CHAT_PREFIX.append(
                    Component.text("Duel request to ", NamedTextColor.RED)
                            .append(Component.text(opponent.getName(), NamedTextColor.GOLD))
                            .append(Component.text(" expired since they left the server", NamedTextColor.RED))
            ));
            challenger.playSound(challenger, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);

            activeProposals.remove(this);
        }, 1, 1);
    }

    public static boolean acceptRequest(Player opponent, Player challenger) {
        //order is no mistake!!!
        ProposedDuel duel = findDuel(challenger, opponent);

        if (duel == null)
            return false;

        duel.accepted = true;

        List.of(challenger, opponent).forEach(player -> player.playSound(player, Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.5f));
        challenger.sendMessage(ServerFest.CHAT_PREFIX.append(
                Component.text(opponent.getName(), NamedTextColor.GOLD)
                        .append(Component.text(" accepted your duel request", NamedTextColor.GREEN))
        ));
        opponent.sendMessage(ServerFest.CHAT_PREFIX.append(
                Component.text("Accepted duel request from ", NamedTextColor.GREEN)
                        .append(Component.text(challenger.getName(), NamedTextColor.GOLD))
        ));

        challenger.getScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
            activeProposals.remove(duel);

            if (!opponent.isValid() || !opponent.isOnline()) {
                challenger.sendMessage(ServerFest.CHAT_PREFIX.append(
                        Component.text("Duel cancelled since ", NamedTextColor.RED)
                                .append(Component.text(opponent.getName(), NamedTextColor.GOLD))
                                .append(Component.text(" quit", NamedTextColor.RED))
                ));
                challenger.playSound(challenger, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                return;
            }

            List.of(challenger, opponent).forEach(player -> {
                //TODO: open DuelGUI
            });
        }, () -> {
            activeProposals.remove(duel);

            opponent.sendMessage(ServerFest.CHAT_PREFIX.append(
                    Component.text("Duel cancelled since ", NamedTextColor.RED)
                            .append(Component.text(challenger.getName(), NamedTextColor.GOLD))
                            .append(Component.text(" quit", NamedTextColor.RED))
            ));
            opponent.playSound(challenger, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }, 20);

        return true;
    }

    public static boolean declineRequest(Player opponent, Player challenger) {
        ProposedDuel duel = findDuel(challenger, opponent);

        if (duel == null)
            return false;

        challenger.sendMessage(ServerFest.CHAT_PREFIX.append(
                Component.text(opponent.getName(), NamedTextColor.GOLD)
                        .append(Component.text(" declined your duel request", NamedTextColor.RED))
        ));
        challenger.playSound(challenger, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

        opponent.sendMessage(ServerFest.CHAT_PREFIX.append(
                Component.text("Declined duel request from ", NamedTextColor.RED)
                        .append(Component.text(challenger.getName(), NamedTextColor.GOLD))
        ));
        opponent.playSound(challenger, Sound.BLOCK_LEVER_CLICK, 1.0f, 1.0f);

        activeProposals.remove(duel);
        return true;
    }

    private static ProposedDuel findDuel(Player challenger, Player opponent) {
        return activeProposals.stream().filter(proposal -> proposal.challenger.equals(challenger) && proposal.opponent.equals(opponent))
                .findFirst().orElse(null);
    }
}
