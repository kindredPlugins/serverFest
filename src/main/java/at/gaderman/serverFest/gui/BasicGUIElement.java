package at.gaderman.serverFest.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface BasicGUIElement extends GUIElement{
    Material iconType();
    Component iconName();
    List<Component> iconLore();

    @Override
    default ItemStack itemRepresentation() {
        ItemStack item = ItemStack.of(iconType());
        item.editMeta(meta -> {
            meta.itemName(iconName());
            meta.lore(iconLore());
        });
        return item;
    }
}
