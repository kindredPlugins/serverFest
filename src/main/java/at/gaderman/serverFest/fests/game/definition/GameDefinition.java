package at.gaderman.serverFest.fests.game.definition;

import at.gaderman.serverFest.gui.BasicGUIElement;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;

/**
 * Represents the frontend facing definition of a game, these are  consistent data which are used in GUIs
 * and will be the same for every instance
 */
public interface GameDefinition extends BasicGUIElement {

    Material iconType();

    Component iconName();

    List<Component> iconLore();

}
