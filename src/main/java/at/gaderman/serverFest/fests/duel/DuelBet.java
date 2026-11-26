package at.gaderman.serverFest.fests.duel;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.SaveStateManagement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Holds the necessary temporary data when a duel involves a bet
/// The collection and payout is handled by {@link SaveStateManagement} which holds it for
/// safety and also applies it when triggered
public record DuelBet(Player challenger, Player opponent,
                      List<ItemStack> challengerItems, List<ItemStack> opponentItems,
                      double challengerCurrencyBet, double opponentCurrencyBet) {

    /// Pays out the bet for the given player
    public void payOut(Player player) {
        if (challengerItems.isEmpty() && opponentItems.isEmpty() && challengerCurrencyBet <= 0 && opponentCurrencyBet <= 0)
            return;

        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.65f);
        List<ItemStack> items = new ArrayList<>(challengerItems);
        items.addAll(opponentItems);

        if (!items.isEmpty()) {
            //TODO: this is called when the player goes offline too, so we essentially stack up items which is "fine" but not the best solution
            Map<Integer, ItemStack> left = player.getInventory().addItem(items.toArray(ItemStack[]::new));

            for (ItemStack rest : left.values()) {
                Item item = player.getWorld().dropItem(player.getLocation(), rest);
                item.setOwner(player.getUniqueId());
                item.setGravity(false);
                item.setVelocity(new Vector(0, 0, 0));
                item.setUnlimitedLifetime(true);
            }
            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(Component.text("Your ", NamedTextColor.WHITE))
                    .append(Component.text("bet ", NamedTextColor.GOLD))
                    .append(Component.text("for winning the duel has been delivered.", NamedTextColor.WHITE))
            );
        }

        Economy economy = ServerFest.getPlugin().getEconomy();
        double currencyPayOut = challengerCurrencyBet + opponentCurrencyBet;
        if (economy != null && currencyPayOut > 0) {
            economy.depositPlayer(player, currencyPayOut);
            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(Component.text("Received ", NamedTextColor.WHITE))
                    .append(Component.text(economy.format(currencyPayOut), NamedTextColor.GOLD))
                    .append(Component.text(" for winning a duel.", NamedTextColor.WHITE))
            );
        }
    }
}
