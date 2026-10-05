package at.gaderman.serverFest.gui.fests;

import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.game.definition.GameDefinition;
import at.gaderman.serverFest.fests.schedule.FixedDateScheduled;
import at.gaderman.serverFest.gui.ActionInventory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.eclipse.sisu.Nullable;

import java.time.LocalDateTime;

public class FestCreationGUI extends ActionInventory {

    private FestType festType = FestType.CASUAL;
    private @Nullable GameType gameType;
    private int minPlayers = 2;

    public FestCreationGUI() {
        super(36, Component.text("Fest Creation", NamedTextColor.GOLD));
        updateInventory();
    }


    /**
     * 0  1  2  3  4  5  6  7  8
     * 9  10 11 12 13 14 15 16 17
     * 18 19 20 21 22 23 24 25 26
     * <p>
     * -  -  -  -  -  -  -  -  -
     * F  G  M  -  T  -  Ti U  Of
     *
     */
    private static final int FEST_TYPE_SLOT = 9;
    private static final int GAME_TYPE_SLOT = 10;
    private static final int MIN_PLAYERS_SLOT = 11;

    private static final int ACCEPT_SLOT = 30;

    @Override
    protected void initializeInventory(int size, Component inventoryName) {
        super.initializeInventory(size, inventoryName);

        for (int i = 0; i < size; i++)
            inventory.setItem(i, ItemStack.of(Material.GRAY_STAINED_GLASS_PANE));
    }

    private void updateInventory(){
        updateFestType();
        defineInventoryAction(FEST_TYPE_SLOT, event -> {
            festType = FestType.values()[(festType.ordinal() + 1) % FestType.values().length];
            updateFestType();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);
        updateGameType();
        defineInventoryAction(GAME_TYPE_SLOT, event -> {
            int increment = event.isLeftClick() ? 1 : -1;
            int newIndex = gameType == null ? -1 + increment : gameType.ordinal() + increment;

            if(newIndex == -1)
                gameType = null;
            else if(newIndex < -1)
                gameType = GameType.values()[GameType.values().length - 1];
            else if(newIndex >= GameType.values().length)
                gameType = null;
            else
                gameType = GameType.values()[newIndex];

            updateGameType();
        }, Sound.ENTITY_CHICKEN_EGG, 1, 1);

        inventory.setItem(ACCEPT_SLOT, ItemStack.of(Material.GREEN_WOOL));
        defineInventoryAction(ACCEPT_SLOT, _ -> {
            FixedDateScheduled.create(festType, gameType, minPlayers, LocalDateTime.now().plusSeconds(5));
            inventory.close();
        }, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    private void updateFestType() {
        Material type = switch (festType) {
            case CASUAL -> Material.FIREWORK_ROCKET;
            case SERVER_COMPETITION -> Material.DIAMOND;
            case DUEL -> Material.DIAMOND_SWORD;
        };

        ItemStack item = new ItemStack(type);
        item.editMeta(meta -> {
            Component displayName = switch (festType) {
                case CASUAL -> Component.text("Casual Fest", NamedTextColor.GREEN);
                case SERVER_COMPETITION -> Component.text("Server Competition", NamedTextColor.RED);
                case DUEL -> Component.text("Duel", NamedTextColor.BLUE);
            };
            meta.itemName(displayName);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });

        inventory.setItem(FEST_TYPE_SLOT, item);
    }

    private void updateGameType() {
        GameDefinition gameDefinition = gameType == null ? null : gameType.definition();
        Material type = gameDefinition == null ? Material.BARRIER : gameDefinition.iconType();

        ItemStack item = new ItemStack(type);
        item.editMeta(meta -> {
            meta.itemName(gameDefinition == null
                    ? Component.text("Random Game", NamedTextColor.GOLD)
                    : gameDefinition.iconName());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });

        inventory.setItem(GAME_TYPE_SLOT, item);
    }
}
