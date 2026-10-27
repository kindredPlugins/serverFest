package at.gaderman.serverFest.gui.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestManager;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.duel.DuelQueueManager;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.gui.ActionInventory;
import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DuelConfirmationGUI extends ActionInventory {

    private static final int GAMETYPE_SLOT = 13;
    private static final int CHALLENGER_ACCEPT_SLOT = 20;
    private static final int OPPONENT_ACCEPT_SLOT = 24;

    private final Player challenger;
    private final Player opponent;

    private boolean challengerAccepted;
    private boolean opponentAccepted;

    private @Nullable GameType gameType;

    private boolean cancelled;

    public DuelConfirmationGUI(Player challenger, Player opponent) {
        super(27, Component.text("Duel", NamedTextColor.AQUA));
        this.challenger = challenger;
        this.opponent = opponent;

        initInv();
    }

    private void initInv() {
        fillWithFillItem();

        inventory.setItem(4, duelIcon());

        inventory.setItem(11, playerHead(challenger));
        inventory.setItem(15, playerHead(opponent));

        updateGameType();
        defineInventoryAction(GAMETYPE_SLOT, event -> {
            int ordinal = gameType == null ? -1 : gameType.ordinal();
            int nextSlot = ordinal + (event.isLeftClick() ? 1 : -1);
            if (nextSlot >= GameType.values().length)
                gameType = null;
            else if (nextSlot < 0)
                gameType = GameType.values()[GameType.values().length - 1];

            else gameType = GameType.values()[nextSlot];

            updateGameType();
            challengerAccepted = false;
            opponentAccepted = false;
            updateAcceptance();

            List.of(challenger, opponent).forEach(player -> player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 1f, 1f));
        });

        updateAcceptance();
        defineInventoryAction(CHALLENGER_ACCEPT_SLOT, event -> {
            if (!(event.getWhoClicked() instanceof Player player))
                return;

            if (!player.equals(challenger)) {
                player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
                return;
            }

            challengerAccepted = !challengerAccepted;
            List.of(challenger, opponent).forEach(listener -> listener.playSound(player, challengerAccepted ?
                    Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f));

            updateAcceptance();
        });
        defineInventoryAction(OPPONENT_ACCEPT_SLOT, event -> {
            if (!(event.getWhoClicked() instanceof Player player))
                return;

            if (!player.equals(opponent)) {
                player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
                return;
            }

            opponentAccepted = !opponentAccepted;
            List.of(challenger, opponent).forEach(listener -> listener.playSound(player, opponentAccepted ?
                    Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f));

            updateAcceptance();
        });
    }

    private void updateAcceptance() {
        inventory.setItem(CHALLENGER_ACCEPT_SLOT, obtainAcceptItem(challenger, challengerAccepted));
        inventory.setItem(OPPONENT_ACCEPT_SLOT, obtainAcceptItem(opponent, opponentAccepted));

        if (challengerAccepted && opponentAccepted) {
            inventory.close();

            List<Player> players = List.of(challenger, opponent);
            players.forEach(player -> {
                player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.3f);
            });

            challenger.getScheduler().runDelayed(ServerFest.getPlugin(), _ -> {
                if (!opponent.isConnected() || FestManager.getInstance().isPlayerParticipatingInAnyFest(opponent)) {
                    challenger.playSound(challenger, Sound.ENTITY_VILLAGER_DEATH, 1f, 0.5f);
                    challenger.sendMessage(Component.text("Duel cancelled", NamedTextColor.RED));
                    return;
                }

                if(FestManager.getInstance().isPlayerParticipatingInAnyFest(challenger)){
                    List.of(challenger, opponent).forEach(both -> {
                        both.playSound(both.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1f, 0.5f);
                        both.sendMessage(Component.text("Duel was cancelled due to participating in another fest", NamedTextColor.RED));
                    });
                    return;
                }

                List<GameType> chooseTypes = Arrays.stream(GameType.values()).collect(Collectors.toList());
                GameType game = gameType;

                while (game == null && !chooseTypes.isEmpty()) {
                    game = chooseTypes.remove((int) (Math.random() * chooseTypes.size()));

                    if (ServerFest.arenaManager.requestArena(game, FestType.DUEL) == null)
                        game = null;
                }

                if (game == null || ServerFest.arenaManager.requestArena(game, FestType.DUEL) == null) {
                    Component gameDisplay = game == null ? Component.text("any game")
                            : game.definition().iconName();

                    players.forEach(player -> {
                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.2f);
                        player.sendMessage(ServerFest.CHAT_PREFIX
                                .append(Component.text("there is no arena ready for ", NamedTextColor.RED))
                                .append(gameDisplay)
                        );
                    });

                    DuelQueueManager.getInstance().enqueue(challenger, opponent, gameType);
                    return;
                }

                if (FestManager.getInstance().isPlayerParticipatingInAnyFest(challenger)
                        || FestManager.getInstance().isPlayerParticipatingInAnyFest(opponent)) {
                    List.of(challenger, opponent).forEach(both -> {
                        both.playSound(both.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1f, 0.5f);
                        both.sendMessage(Component.text("Duel was cancelled due to someone participating in another fest", NamedTextColor.RED));
                    });
                    return;
                }

                Fest fest = new Fest(FestType.DUEL, game, new ArrayList<>(List.of(challenger.getUniqueId(), opponent.getUniqueId())));
                fest.start();
            }, () -> {
                if (opponent.isConnected()) {
                    opponent.playSound(challenger, Sound.ENTITY_VILLAGER_DEATH, 1f, 0.5f);
                    opponent.sendMessage(Component.text("Duel cancelled", NamedTextColor.RED));
                }
            }, 20);
        }
    }

    private ItemStack duelIcon() {
        ItemStack item = ItemStack.of(Material.IRON_SWORD);
        item.editMeta(meta -> {
            meta.itemName(Component.text("Duel", NamedTextColor.AQUA));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    private ItemStack playerHead(Player player) {
        ItemStack item = ItemUtils.getHeadOfPlayer(player);
        item.editMeta(meta -> {
            meta.customName(Component.text(player.getName(), NamedTextColor.YELLOW));
        });
        return item;
    }

    private void updateGameType() {
        ItemStack item = ItemStack.of(gameType == null ? Material.BARREL : gameType.definition().iconType());
        item.editMeta(meta -> {
            meta.itemName(gameType == null ? Component.text("Random Game", NamedTextColor.RED)
                    : gameType.definition().iconName());
            meta.lore(ItemUtils.applyDefaultLoreStyle(Component.text("Click to change", NamedTextColor.GOLD)));
        });
        inventory.setItem(GAMETYPE_SLOT, item);
    }

    private ItemStack obtainAcceptItem(Player player, boolean accepted) {
        ItemStack item = ItemStack.of(accepted ? Material.LIME_DYE : Material.GRAY_DYE);
        item.editMeta(meta -> {
            meta.itemName(Component.text(accepted ? "Confirm" : "Waiting",
                    accepted ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            meta.lore(ItemUtils.applyDefaultLoreStyle(accepted
                    ? Component.text(player.getName() + " has confirmed", NamedTextColor.YELLOW)
                    : Component.text("Click to confirm " + player.getName(), NamedTextColor.YELLOW)
            ));
        });
        return item;
    }

    @Override
    protected void onSingleClose() {
        super.onSingleClose();

        if(cancelled)
            return;

        if (opponentAccepted && challengerAccepted)
            return;

        cancelled = true;

        inventory.close();
        List.of(challenger, opponent).forEach(player -> {
            player.sendMessage(ServerFest.CHAT_PREFIX.append(Component.text("Duel cancelled", NamedTextColor.RED)));
            player.playSound(player, Sound.ENTITY_WITHER_HURT, 1f, 2f);
        });
    }
}
