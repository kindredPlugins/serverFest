package at.gaderman.serverFest.fests.duel;

import at.gaderman.serverFest.ServerFest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record DuelBet(List<ItemStack> items, double currencyBet) {

    /// Pays out the bet for the given player
    public void payOut(Player player) {
        if (items.isEmpty() && currencyBet <= 0) return;

        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.65f);

        if (!items.isEmpty()) {
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
        if (economy != null && currencyBet > 0) {
            economy.depositPlayer(player, currencyBet);
            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(Component.text("Received ", NamedTextColor.WHITE))
                    .append(Component.text(economy.format(currencyBet), NamedTextColor.GOLD))
                    .append(Component.text(" for winning a duel.", NamedTextColor.WHITE))
            );
        }
    }
}
