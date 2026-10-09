package at.gaderman.serverFest.command;

import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.schedule.FixedDateScheduled;
import at.gaderman.serverFest.gui.fests.FestCreationGUI;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FestCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        boolean isPlayer = sender instanceof Player;

        switch (args.length) {
            case 0 -> {
                return false;
            }
            case 1 -> {
                switch (args[0]) {
                    case "dev" -> {
                        //TODO: add only sender as participant and do not let others join
                        sendScheduleMessage(sender);
                        FixedDateScheduled.create(LocalDateTime.now().plus(10, ChronoUnit.SECONDS));
                        return true;
                    }
                    case "quick" -> {
                        sendScheduleMessage(sender);
                        FixedDateScheduled.create(LocalDateTime.now().plus(30, ChronoUnit.SECONDS));
                        return true;
                    }
                    case "create" -> {
                        if (!isPlayer) {
                            sender.sendMessage(Component.text("Only players can create a fest!", NamedTextColor.RED));
                            return true;
                        }

                        new FestCreationGUI().openInventory((Player) sender);
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
            case 2 -> {
                if (args[0].equals("quick")) {
                    try {
                        GameType gameType = GameType.valueOf(args[1].toUpperCase());
                        FixedDateScheduled.create(gameType, LocalDateTime.now().plusSeconds(5));
                        sendScheduleMessage(sender);
                    } catch (IllegalArgumentException exception) {
                        sender.sendMessage(Component.text("Invalid gameType " + args[1], NamedTextColor.RED));
                    }
                    return true;
                }
            }
        }

        FixedDateScheduled.create(LocalDateTime.now().plus(10, ChronoUnit.SECONDS));
        //RecurringDateScheduled.create(LocalDateTime.now().plus(5, ChronoUnit.SECONDS), ChronoUnit.MINUTES, 5);

        return false;
    }

    private void sendScheduleMessage(CommandSender sender) {
        if (sender instanceof Player playerSender) {
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<sprite:\"minecraft:items\":item/firework_rocket> <head:" + playerSender.getUniqueId() + "> ")
                        .append(Component.text(" just scheduled a new fest!", NamedTextColor.GRAY)));
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.6f);
            });
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        List<String> completions = new ArrayList<>();

        switch (args.length) {
            case 1 -> {
                completions.add("dev");
                completions.add("quick");
                completions.add("create");
            }
            case 2 -> {
                if (args[0].equals("quick")) {
                    completions.addAll(Arrays.stream(GameType.values())
                            .map(type -> type.name().toLowerCase())
                            .filter(name -> name.startsWith(args[1]))
                            .toList());
                }
            }
        }

        return completions;
    }
}