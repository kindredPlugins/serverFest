package at.gaderman.serverFest.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public class ItemRemoval {
    private ItemRemoval() {}

    private static final class Need {
        final ItemStack template;
        int amount;

        Need(ItemStack template, int amount) {
            this.template = template;
            this.amount = amount;
        }
    }

    /**
     * Merges a list of stacks into one total per item type. Two entries of the same item
     * (e.g. 64 + 36 diamonds from two grid slots) become one need of 100.
     */
    private static List<Need> aggregate(List<ItemStack> items) {
        List<Need> needs = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) continue;

            Need existing = null;
            for (Need need : needs) {
                if (need.template.isSimilar(item)) {
                    existing = need;
                    break;
                }
            }

            if (existing != null) existing.amount += item.getAmount();
            else needs.add(new Need(item.asOne(), item.getAmount()));   // amount tracked separately, stacks can't hold totals
        }
        return needs;
    }

    /**
     * Checks if the player has enough items in total as the given list, same algorithm used by {@link #removeExactly(Player, List)}
     */
    public static boolean hasAll(Player player, List<ItemStack> items) {
        PlayerInventory inventory = player.getInventory();

        for (Need need : aggregate(items)) {
            if (ItemUtils.countItems(inventory, need.template) < need.amount) return false;
        }
        return true;
    }

    /**
     * Removes exactly the given items from the player's inventory, across however many stacks they are spread over.
     * Does not modify anything if not enough items are present.
     *
     * @return true if everything was removed, false if the player is missing something (inventory untouched)
     */
    public static boolean removeExactly(Player player, List<ItemStack> items) {
        PlayerInventory inventory = player.getInventory();
        List<Need> needs = aggregate(items);

        for (Need need : needs) {
            if (ItemUtils.countItems(inventory, need.template) < need.amount) return false;
        }

        for (Need need : needs) {
            int left = need.amount;

            for (int slot = 0; slot < inventory.getSize() && left > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack == null || stack.isEmpty() || !need.template.isSimilar(stack)) continue;

                int take = Math.min(left, stack.getAmount());
                left -= take;

                if (take == stack.getAmount()) {
                    inventory.setItem(slot, null);
                } else {
                    stack.setAmount(stack.getAmount() - take);
                    inventory.setItem(slot, stack);
                }
            }
        }

        return true;
    }
}
