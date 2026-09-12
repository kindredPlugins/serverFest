package at.gaderman.serverFest.command;

import at.gaderman.serverFest.fests.schedule.FixedDateScheduled;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FestCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if(sender instanceof Player playerSender) {
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<sprite:\"minecraft:items\":item/firework_rocket> <head:" + playerSender.getUniqueId() + "> ")
                        .append(Component.text(" just scheduled a new fest!", NamedTextColor.GRAY)));
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.6f);
            });
        }

        new FixedDateScheduled().schedule();

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        return List.of();
    }
}