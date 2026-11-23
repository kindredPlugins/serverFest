package at.gaderman.serverFest.fests.duel;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.fests.SaveStateManagement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
            //TODO: leftovers are not dropped yet again
            player.give(items, true).drops().forEach(drop -> {
                drop.setOwner(player.getUniqueId());
            });
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
