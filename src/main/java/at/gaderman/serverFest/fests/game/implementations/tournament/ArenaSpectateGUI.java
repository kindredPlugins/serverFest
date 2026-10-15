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

    private final PVPTournament tournament;
    private final List<Player> players;
    private int page;
    private int maxPages;

    public ArenaSpectateGUI(PVPTournament tournament, List<PVPTournament.FaceOff> faceOffs) {
        super(Component.text("Spectate a player", NamedTextColor.YELLOW));
        this.tournament = tournament;
        this.players = faceOffs.stream().flatMap(faceOff -> faceOff.players().stream()).toList();

        updateInventory();
    }


    @Override
    protected int calculateSize() {
        return super.calculateSize();
    }

    private void updateInventory() {
        fillEmptyWithClearFill();
        for (int i = inventory.getSize() - 9; i < inventory.getSize(); i++) {
            inventory.setItem(i, getFillItem());
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            defineInventoryAction(i, null);
        }

        for (int i = 0; i < inventory.getSize() - 9 && i < (i + page * (inventory.getSize() - 9)) * players.size(); i++) {
            int index = i + page * (inventory.getSize() - 9);

            Player toVisit = players.get(index);
            ItemStack head = ItemUtils.getHeadOfPlayer(toVisit);
            head.editMeta(meta -> {
                meta.customName(Component.text(toVisit.getName()).decoration(TextDecoration.ITALIC, false));
                meta.lore(ItemUtils.applyDefaultLoreStyle(Component.text("Click to spectate the match of " + toVisit.getName(), NamedTextColor.GRAY)));
            });
            inventory.setItem(i, head);
            defineInventoryAction(i, event -> {
                if (!(event.getWhoClicked() instanceof Player player))
                    return;

                boolean success = tournament.requestArenaVisitFor(player, toVisit);
                if (success)
                    player.closeInventory();
                else {
                    player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1, 1);
                    updateInventory();
                }
            });
        }

        if(page > 0) {
            int prevSlot = inventory.getSize() - 9;
            inventory.setItem(prevSlot,
                    ItemUtils.createWithBasicName(Material.ARROW, Component.text("Previous Page")));
            defineInventoryAction(prevSlot, _ -> {
                page--;
                updateInventory();
            }, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        }

        if(page < maxPages - 1){
            int nextSlot = inventory.getSize() - 1;
            inventory.setItem(nextSlot,
                    ItemUtils.createWithBasicName(Material.ARROW, Component.text("Next Page")));
            defineInventoryAction(nextSlot, _ -> {
                page++;
                updateInventory();
            }, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        }
    }
}
