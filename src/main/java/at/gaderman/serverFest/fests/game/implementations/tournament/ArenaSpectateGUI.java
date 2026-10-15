package at.gaderman.serverFest.fests.game.implementations.tournament;

import at.gaderman.serverFest.gui.ActionInventory;
import at.gaderman.serverFest.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ArenaSpectateGUI extends ActionInventory {

    private static final int INVENTORY_SIZE = 54;
    private static final int NAVIGATION_ROW_SIZE = 9;
    private static final int PLAYERS_PER_PAGE = INVENTORY_SIZE - NAVIGATION_ROW_SIZE;

    private final PVPTournament tournament;
    private List<Player> players;
    private int maxPages;
    private int page = 0;

    public ArenaSpectateGUI(PVPTournament tournament, List<PVPTournament.FaceOff> faceOffs) {
        super(Component.text("Spectate a player", NamedTextColor.YELLOW));
        this.tournament = tournament;
        this.players = tournament.battlingPlayers();
        updateMaxPages();
    }

    @Override
    protected int calculateSize() {
        return INVENTORY_SIZE;
    }

    private void updateMaxPages() {
        this.maxPages = Math.max(1, (int) Math.ceil(players.size() / (double) PLAYERS_PER_PAGE));
    }

    private void updateInventory() {
        page = Math.clamp(page, 0, maxPages - 1);

        inventory.clear();
        for (int i = 0; i < inventory.getSize(); i++) {
            removeInventoryAction(i);
        }

        fillEmptyWithClearFill();
        for (int i = INVENTORY_SIZE - NAVIGATION_ROW_SIZE; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, getFillItem());
        }

        int startIndex = page * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, players.size());

        for (int index = startIndex; index < endIndex; index++) {
            int slot = index - startIndex;

            Player toVisit = players.get(index);
            ItemStack head = ItemUtils.getHeadOfPlayer(toVisit);
            head.editMeta(meta -> {
                meta.customName(Component.text(toVisit.getName()).decoration(TextDecoration.ITALIC, false));
                meta.lore(ItemUtils.applyDefaultLoreStyle(
                        Component.text("Click to spectate the match of " + toVisit.getName(), NamedTextColor.GRAY)));
            });
            inventory.setItem(slot, head);

            defineInventoryAction(slot, event -> {
                if (!(event.getWhoClicked() instanceof Player player))
                    return;

                boolean success = tournament.requestArenaVisitFor(player, toVisit);
                if (success)
                    player.closeInventory();
                else {
                    player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1, 1);

                    players = tournament.battlingPlayers();
                    updateMaxPages();
                    updateInventory();
                }
            });
        }

        inventory.setItem(INVENTORY_SIZE - 5, ItemUtils.createWithBasicName(
                Material.PAPER,
                Component.text("Page " + (page + 1) + " / " + maxPages, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)));

        if (page > 0) {
            int prevSlot = INVENTORY_SIZE - NAVIGATION_ROW_SIZE;
            inventory.setItem(prevSlot,
                    ItemUtils.createWithBasicName(Material.ARROW, Component.text("Previous Page")));
            defineInventoryAction(prevSlot, _ -> {
                page--;
                updateInventory();
            }, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        }

        if (page < maxPages - 1) {
            int nextSlot = INVENTORY_SIZE - 1;
            inventory.setItem(nextSlot,
                    ItemUtils.createWithBasicName(Material.ARROW, Component.text("Next Page")));
            defineInventoryAction(nextSlot, _ -> {
                page++;
                updateInventory();
            }, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        }
    }

    @Override
    public void openInventory(Player player) {
        super.openInventory(player);
        updateInventory();
    }
}