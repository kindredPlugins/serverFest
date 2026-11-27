package at.gaderman.serverFest.gui.fests;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestManager;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.duel.DuelBet;
import at.gaderman.serverFest.fests.duel.DuelQueueManager;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.gui.ActionInventory;
import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class DuelConfirmationGUI extends ActionInventory {

    private static final int GAMETYPE_SLOT = 4;
    private static final int CHALLENGER_ACCEPT_SLOT = 11;
    private static final int OPPONENT_ACCEPT_SLOT = 15;

    private static final int GRID_START_SLOT = 27;
    private static final int GRID_OPPONENT_OFFSET = 5;
    private static final int CHALLENGER_CURRENCY_SLOT = GRID_START_SLOT + 9 + 3;
    private static final int OPPONENT_CURRENCY_SLOT = CHALLENGER_CURRENCY_SLOT + GRID_OPPONENT_OFFSET;

    private final Player challenger;
    private final Player opponent;

    private boolean challengerAccepted;
    private boolean opponentAccepted;

    private @Nullable GameType gameType;

    private double challengerCurrencyBet;
    private double opponentCurrencyBet;

    private boolean cancelled;

    //region Init

    public DuelConfirmationGUI(Player challenger, Player opponent) {
        super(54, Component.text("Duel", NamedTextColor.AQUA));
        this.challenger = challenger;
        this.opponent = opponent;

        initInv();
    }

    private void initInv() {
        fillWithFillItem();

        //inventory.setItem(4, duelIcon());

        inventory.setItem(CHALLENGER_ACCEPT_SLOT - 9, playerHead(challenger));
        inventory.setItem(OPPONENT_ACCEPT_SLOT - 9, playerHead(opponent));

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

        for (int i = 18; i < 27; i++)
            inventory.setItem(i, getFillItem().withType(Material.IRON_BARS));

        for (int i = 0; i < 3; i++) {
            int row = GRID_START_SLOT + 9 * i;

            for (int j = 0; j < 3; j++) {
                inventory.setItem(row + j, ItemStack.empty());
                inventory.setItem(row + j + GRID_OPPONENT_OFFSET, ItemStack.empty());
            }
        }

        Economy economy = ServerFest.getPlugin().getEconomy();
        if(economy != null){
            inventory.setItem(CHALLENGER_CURRENCY_SLOT, createCurrencyBetItem(challenger, 0));
            inventory.setItem(OPPONENT_CURRENCY_SLOT, createCurrencyBetItem(opponent, 0));

            BiConsumer<InventoryClickEvent, Boolean> betChanger = (event, isChallenger) -> {
                if(!(event.getWhoClicked() instanceof Player player)) return;

                if(isChallenger && !player.equals(challenger)) return;
                if(!isChallenger && !player.equals(opponent)) return;

                int increment = event.isLeftClick() ? 1 : -1;
                if(event.isShiftClick()) increment *= 10;

                double bet = isChallenger ? challengerCurrencyBet : opponentCurrencyBet;

                double value = Math.max(bet + increment, 0);
                if(bet <= 0 && value <= 0){
                    player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    return;
                }

                if(!economy.has(player, value)){
                    player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
                    value = Math.max(0, economy.getBalance(player));
                }else {
                    player.playSound(player, Sound.BLOCK_NETHER_GOLD_ORE_BREAK, 1f, 1f);
                }

                if(isChallenger) challengerCurrencyBet = value;
                else opponentCurrencyBet = value;

                inventory.setItem(isChallenger ? CHALLENGER_CURRENCY_SLOT : OPPONENT_CURRENCY_SLOT, createCurrencyBetItem(player, value));
                resetAcceptance();
            };

            defineInventoryAction(CHALLENGER_CURRENCY_SLOT, event -> betChanger.accept(event, true));
            defineInventoryAction(OPPONENT_CURRENCY_SLOT, event -> betChanger.accept(event, false));
        }
    }

    //endregion

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

                if (FestManager.getInstance().isPlayerParticipatingInAnyFest(challenger)) {
                    List.of(challenger, opponent).forEach(both -> {
                        both.playSound(both.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1f, 0.5f);
                        both.sendMessage(Component.text("Duel was cancelled due to participating in another fest", NamedTextColor.RED));
                    });
                    return;
                }

                DuelBet duelBet = collectBet();

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

                    DuelQueueManager.getInstance().enqueue(challenger, opponent, gameType, duelBet);
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

                Fest fest = Fest.ofDuel(game, new ArrayList<>(List.of(challenger.getUniqueId(), opponent.getUniqueId())), duelBet);
                fest.start();
            }, () -> {
                if (opponent.isConnected()) {
                    opponent.playSound(challenger, Sound.ENTITY_VILLAGER_DEATH, 1f, 0.5f);
                    opponent.sendMessage(Component.text("Duel cancelled", NamedTextColor.RED));
                }
            }, 20);
        }
    }

    //region Items

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
            meta.customName(Component.text(player.getName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        });
        return item;
    }

    private void updateGameType() {
        ItemStack item = ItemStack.of(gameType == null ? Material.BARREL : gameType.definition().iconType());
        item.editMeta(meta -> {
            meta.itemName(gameType == null ? Component.text("Random Game", NamedTextColor.RED)
                    : gameType.definition().iconName());
            meta.lore(ItemUtils.applyDefaultLoreStyle(Component.text("Click to change", NamedTextColor.GOLD)));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
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

    private ItemStack createCurrencyBetItem(Player player, double amount){
        Economy economy = ServerFest.getPlugin().getEconomy();
        if(economy == null)
            return getFillItem();

        ItemStack item = ItemStack.of(Material.SUNFLOWER, (int) Math.ceil(Math.clamp(amount, 1, 99)));
        item.editMeta(meta -> {
            meta.itemName(Component.text("Bet: ", NamedTextColor.GRAY)
                    .append(Component.text(economy.format(amount), NamedTextColor.GOLD))
            );
            meta.lore(ItemUtils.applyDefaultLoreStyle(Component.text("Bet of ", NamedTextColor.DARK_GRAY)
                    .append(player.name())));

            meta.setEnchantmentGlintOverride(amount > 0);
        });
        return item;
    }

    //endregion

    //region Custom Events

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.isEmpty()) return;

        boolean shift = click.isShiftClick();
        boolean right = click == ClickType.RIGHT;
        int gridStart = GRID_START_SLOT + (challenger.getUniqueId().equals(player.getUniqueId()) ? 0 : GRID_OPPONENT_OFFSET);

        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            int available = ItemUtils.countItems(player.getInventory(), clicked) - countItemInGrid(gridStart, clicked);
            int wanted = Math.min(available, shift ? clicked.getAmount() : right ? (clicked.getAmount() + 1) / 2 : 1);

            int placed = 0;
            if (wanted > 0) {
                ItemStack offer = clicked.clone();
                offer.setAmount(wanted);
                placed = placeItemInGrid(gridStart, offer);
            }

            if(placed > 0) resetAcceptance();
            player.playSound(player, placed > 0 ? Sound.ENTITY_CHICKEN_EGG : Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        } else {
            int rel = event.getSlot() - gridStart;
            if (rel < 0 || rel / 9 > 2 || rel % 9 > 2) return;

            int amount = shift ? clicked.getAmount() : right ? (clicked.getAmount() + 1) / 2 : 1;
            int removed = removeFromSlot(event.getSlot(), amount);

            if (removed > 0){
                player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 1f, 1.25f);

                resetAcceptance();
            }
        }
    }

    private void resetAcceptance(){
        if(challengerAccepted || opponentAccepted)
            List.of(challenger, opponent).forEach(both -> both.playSound(both, Sound.BLOCK_LEVER_CLICK, 1f, 1f));

        challengerAccepted = false;
        opponentAccepted = false;
        updateAcceptance();
    }

    private int removeFromSlot(int slot, int amount) {
        ItemStack at = inventory.getItem(slot);
        if (at == null || at.isEmpty()) return 0;

        int removed = Math.min(amount, at.getAmount());
        if (removed >= at.getAmount()) {
            inventory.setItem(slot, null);
        } else {
            at.setAmount(at.getAmount() - removed);
            inventory.setItem(slot, at);
        }
        return removed;
    }

    private int removeFromGrid(int gridStart, ItemStack template) {
        int removed = 0;
        for (int i = 0; i < 9; i++) {
            int slot = gridStart + (i / 3) * 9 + (i % 3);
            ItemStack at = inventory.getItem(slot);
            if (at != null && !at.isEmpty() && template.isSimilar(at))
                removed += removeFromSlot(slot, at.getAmount());
        }
        return removed;
    }

    /// Counts how often the item is already present in the bet grid
    private int countItemInGrid(int gridStart, @NonNull ItemStack item) {
        int sum = 0;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                ItemStack itemAt = inventory.getItem(gridStart + row * 9 + col);
                if (itemAt != null && item.isSimilar(itemAt))
                    sum += itemAt.getAmount();
            }
        }

        return sum;
    }

    /// Places as much of the given item as possible within the grid
    ///
    /// @return Amount of items successfully placed
    private int placeItemInGrid(int gridStart, ItemStack item) {
        int left = item.getAmount();

        // 2 iterations, 0 - fill existing 1 - fill empty
        for (int iteration = 0; iteration < 2 && left > 0; iteration++) {
            for (int i = 0; i < 9 && left > 0; i++) {
                int index = gridStart + (i / 3) * 9 + (i % 3);
                ItemStack at = inventory.getItem(index);

                if (iteration == 0 && at != null && !at.isEmpty() && item.isSimilar(at)) {
                    int add = Math.min(at.getMaxStackSize() - at.getAmount(), left);
                    if (add > 0) {
                        at.setAmount(at.getAmount() + add);
                        inventory.setItem(index, at);
                        left -= add;
                    }
                } else if (iteration == 1 && (at == null || at.isEmpty())) {
                    int add = Math.min(item.getMaxStackSize(), left);
                    ItemStack placed = item.clone();
                    placed.setAmount(add);
                    inventory.setItem(index, placed);
                    left -= add;
                }
            }
        }
        return item.getAmount() - left;
    }

    /// collects all items from the grids and set bet currency into a {@link DuelBet}
    private DuelBet collectBet(){
        List<ItemStack> challengerItems = new ArrayList<>();
        List<ItemStack> opponentItems = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int index = GRID_START_SLOT + i * 9 + j;

                ItemStack chItem = inventory.getItem(index);
                if(chItem != null && !chItem.isEmpty())
                    challengerItems.add(chItem.clone());

                ItemStack opItem = inventory.getItem(index + GRID_OPPONENT_OFFSET);
                if(opItem != null && !opItem.isEmpty())
                    opponentItems.add(opItem.clone());
            }
        }

        return new DuelBet(challenger, opponent, challengerItems, opponentItems, challengerCurrencyBet, opponentCurrencyBet);
    }

    @Override
    protected void onSingleClose() {
        super.onSingleClose();

        if (cancelled)
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

    //endregion
}
