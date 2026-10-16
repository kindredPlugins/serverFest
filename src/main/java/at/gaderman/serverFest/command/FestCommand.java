package at.gaderman.serverFest.command;

import at.gaderman.serverFest.fests.Fest;
import at.gaderman.serverFest.fests.FestManager;
import at.gaderman.serverFest.fests.FestType;
import at.gaderman.serverFest.fests.duel.ProposedDuel;
import at.gaderman.serverFest.fests.game.GameType;
import at.gaderman.serverFest.fests.recruitment.FestRecruitment;
import at.gaderman.serverFest.fests.recruitment.RecruitmentManager;
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
                        FestRecruitment.requestRecruitment(10, FestType.CASUAL, null);
                        return true;
                    }
                    case "quick" -> {
                        sendScheduleMessage(sender);
                        FestRecruitment.requestRecruitment(60, FestType.CASUAL, null);
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
                    case "quit" -> {
                        if(!isPlayer){
                            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
                            return true;
                        }

                        Player player = (Player) sender;
                        Fest activeFest = FestManager.getInstance().getFests().stream()
                                .filter(fest -> fest.participantUUIDs().contains(player.getUniqueId()))
                                .findFirst().orElse(null);

                        if(activeFest == null){
                            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
                            player.sendMessage(Component.text("You are not in any fest to quit from!", NamedTextColor.RED));
                            return true;
                        }

                        activeFest.onLeave(player);
                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 1, 1);
                        return true;
                    }
                    case "join" -> {
                        if (!isPlayer) {
                            sender.sendMessage(Component.text("Only players can join a fest!", NamedTextColor.RED));
                            return true;
                        }

                        Player player = (Player) sender;
                        boolean joined = RecruitmentManager.getInstance().addPlayerToActiveRecruitment(player);

                        if (!joined) {
                            if (FestManager.getInstance().isPlayerParticipatingInAnyFest(player))
                                player.sendMessage(Component.text("You are already participating in another fest!", NamedTextColor.RED));
                            else
                                player.sendMessage(Component.text("There is no active fest to join", NamedTextColor.RED));

                            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
                            return true;
                        }

                        return true;
                    }
                }
            }
            case 2 -> {
                if (args[0].equals("quick") || args[0].equals("dev")) {
                    try {
                        boolean isDev = args[0].equals("dev");

                        GameType gameType = GameType.valueOf(args[1].toUpperCase());
                        sendScheduleMessage(sender);
                        var recruitment = FestRecruitment.requestRecruitment(isDev ? 10 : 60, FestType.CASUAL, gameType);
                        if(isPlayer && isDev) recruitment.addParticipant((Player) sender);
                    } catch (IllegalArgumentException exception) {
                        sender.sendMessage(Component.text("Invalid gameType " + args[1], NamedTextColor.RED));
                    }
                    return true;
                }

                if (args[0].equals("duel")) {
                    if (!isPlayer) {
                        sender.sendMessage(Component.text("Only available for players!", NamedTextColor.RED));
                        return true;
                    }

                    Player player = (Player) sender;

                    if(FestManager.getInstance().isPlayerParticipatingInAnyFest(player)){
                        player.sendMessage(Component.text("You cannot duel while participating in another fest!", NamedTextColor.RED));
                        player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
                        return true;
                    }

                    Player opponent = Bukkit.getPlayer(args[1]);

                    if (opponent == null) {
                        player.sendMessage(Component.text("That player is not online or does not exist!", NamedTextColor.RED));
                        player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
                        return true;
                    }

                    if(FestManager.getInstance().isPlayerParticipatingInAnyFest(opponent)){
                        player.sendMessage(Component.text("This player is already participating in another fest!", NamedTextColor.RED));
                        player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
                        return true;
                    }

                    ProposedDuel.propose(player, opponent);
                    return true;
                }
            }
            case 3 -> {
                if (args[0].equals("duel")) {
                    if (!isPlayer) {
                        sender.sendMessage(Component.text("Only available for players!", NamedTextColor.RED));
                        return true;
                    }
                    Player player = (Player) sender;

                    switch (args[1]) {
                        case "accept" -> {
                            Player challenger = Bukkit.getPlayer(args[2]);

                            if (challenger == null) {
                                player.sendMessage(Component.text("That player is not online or does not exist!", NamedTextColor.RED));
                                player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
                                return true;
                            }

                            //side effect call
                            if (!ProposedDuel.acceptRequest(player, challenger)) {
                                player.sendMessage(Component.text("You have no request for a duel from " + challenger.getName() + " or it expired!", NamedTextColor.RED));
                                player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
                            }
                            return true;
                        }

                        case "decline" -> {
                            Player challenger = Bukkit.getPlayer(args[2]);

                            if (challenger == null) {
                                player.sendMessage(Component.text("That player is not online or does not exist!", NamedTextColor.RED));
                                player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
                                return true;
                            }

                            //side effect call
                            if (!ProposedDuel.declineRequest(player, challenger)) {
                                player.sendMessage(Component.text("You have no request for a duel from " + challenger.getName() + " or it expired!", NamedTextColor.RED));
                                player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1, 1);
                            }
                            return true;
                        }
                    }
                }
            }
        }

        sender.sendMessage(Component.text("Usage: /fest <dev|quick|create> [gameType]", NamedTextColor.RED));
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
                completions.add("duel");

                completions.removeIf(completion -> !completion.startsWith(args[0]));
            }
            case 2 -> {
                if (args[0].equals("quick") || args[0].equals("dev")) {
                    completions.addAll(Arrays.stream(GameType.values())
                            .map(type -> type.name().toLowerCase())
                            .filter(name -> name.startsWith(args[1]))
                            .toList());
                }

                if (args[0].equals("duel")) {
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        if (!FestManager.getInstance().isPlayerParticipatingInAnyFest(player))
                            completions.add(player.getName());
                    });
                    completions.removeIf(completion -> !completion.startsWith(args[1]));
                }
            }
        }

        return completions.stream().sorted().toList();
    }
}