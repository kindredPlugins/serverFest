package at.gaderman.serverFest.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ItemUtils {

    public static List<Component> applyDefaultLoreStyle(Component ...lore){
        return applyDefaultLoreStyle(Arrays.stream(lore).collect(Collectors.toList()));
    }

    public static List<Component> applyDefaultLoreStyle(List<Component> lore){
        return lore.stream()
                .map(line -> line.colorIfAbsent(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                .collect(Collectors.toList());
    }

    public static ItemStack getHeadOfPlayer(Player player){
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(meta -> {
            SkullMeta skullMeta = ((SkullMeta) meta);
            skullMeta.setPlayerProfile(player.getPlayerProfile());
        });
        return head;
    }

    public static ItemStack createCustomHead(String skinURL) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, skullMeta -> {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(skinURL.getBytes()));
            PlayerTextures textures = profile.getTextures();
            try {
                textures.setSkin(URI.create(skinURL).toURL());
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Invalid URL for skin: " + skinURL, e);
            }
            profile.setTextures(textures);
            skullMeta.setPlayerProfile(profile);
        });
        return head;
    }

    public static ItemStack dyeLeatherArmor(ItemStack armor, Color color){
        armor.editMeta(meta -> {
            LeatherArmorMeta leatherMeta = (LeatherArmorMeta) meta;
            leatherMeta.setColor(color);
        });
        return armor;
    }

    public static ItemStack createWithBasicName(Material material, Component name){
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.itemName(name);
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    public static ItemStack createBasicUIItem(Material material, Component name, List<Component> lore){
        ItemStack item = createWithBasicName(material, name);
        item.editMeta(meta -> meta.lore(applyDefaultLoreStyle(lore)));
        return item;
    }

    public static Component buildWrappedLine(List<Component> components) {
        return buildWrappedLines(components).getFirst();
    }

    public static List<Component> buildWrappedLines(List<Component> components) {
        List<Component> lines = new ArrayList<>();
        Component currentLine = Component.empty();
        int currentLength = 0;

        for (int i = 0; i < components.size(); i++) {
            Component component = components.get(i);
            boolean isLast = i == components.size() - 1;

            String plainText = PlainTextComponentSerializer.plainText().serialize(component);
            int addedLength = plainText.length() + (isLast ? 0 : 2); // +2 for ", "

            if (currentLength + addedLength > 50 && currentLength > 0) {
                lines.add(currentLine);
                currentLine = Component.empty();
                currentLength = 0;
            }

            currentLine = currentLine.append(component.color(NamedTextColor.BLUE));
            if (!isLast) currentLine = currentLine.append(Component.text(", ", NamedTextColor.GRAY));
            currentLength += addedLength;
        }

        if (currentLength > 0) lines.add(currentLine);
        return lines;
    }

    /**
     * Creates a static RGB gradient across the given text.
     *
     * @param text  The text to color
     * @param start The color of the first character
     * @param end   The color of the last character
     * @return A Component containing the gradient text
     */
    public static Component gradient(
            String text,
            TextColor start,
            TextColor end
    ) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        if (text.length() == 1) {
            return Component.text(text, start);
        }

        TextComponent.Builder builder = Component.text();

        int startRed = start.red();
        int startGreen = start.green();
        int startBlue = start.blue();

        int endRed = end.red();
        int endGreen = end.green();
        int endBlue = end.blue();

        for (int i = 0; i < text.length(); i++) {
            double progress = (double) i / (text.length() - 1);

            int red = lerp(startRed, endRed, progress);
            int green = lerp(startGreen, endGreen, progress);
            int blue = lerp(startBlue, endBlue, progress);

            TextColor color = TextColor.color(red, green, blue);

            builder.append(
                    Component.text(String.valueOf(text.charAt(i)))
                            .color(color)
            );
        }

        return builder.build();
    }

    private static int lerp(int start, int end, double progress) {
        return (int) Math.round(
                start + (end - start) * progress
        );
    }

    public static Component padWithHyphens(Component text, Component hyphenFormat){
        final int LINE_CHARS = 53;
        String plainText = PlainTextComponentSerializer.plainText().serialize(text);
        int hyphenCount = Math.max((LINE_CHARS - (plainText.length() + 2)) / 2, 0);

        Component component = Component.empty();
        for (int i = 0; i < hyphenCount; i++)
            component = component.append(hyphenFormat);

        component = component.appendSpace().append(text).appendSpace();

        for (int i = 0; i < hyphenCount; i++)
            component = component.append(hyphenFormat);

        return component;
    }

    //region Item removal

    private static final class Need {
        final ItemStack template;
        int amount;

        Need(ItemStack template, int amount) {
            this.template = template;
            this.amount = amount;
        }
    }

    /**
     * Removes exactly the given items from the player's inventory, across however many stacks they are spread over.
     * All or nothing: if the player doesn't own everything, nothing is removed.
     *
     * @return true if everything was removed, false if the player is missing something (inventory untouched)
     */
    public static boolean removeExactly(Player player, List<ItemStack> items) {
        PlayerInventory inventory = player.getInventory();

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
            else needs.add(new Need(item.asOne(), item.getAmount()));
        }

        for (Need need : needs) {
            if (count(inventory, need.template) < need.amount) return false;
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

    /** How many of this item type the player carries in total, whatever the stack layout. */
    public static int count(PlayerInventory inventory, ItemStack template) {
        int sum = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.isEmpty() && template.isSimilar(stack))
                sum += stack.getAmount();
        }
        return sum;
    }

    //endregion
}
