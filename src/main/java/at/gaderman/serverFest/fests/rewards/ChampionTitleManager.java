package at.gaderman.serverFest.fests.rewards;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.data.DataConfig;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChampionTitleManager implements Listener {

    private static ChampionTitleManager instance;

    public static ChampionTitleManager getInstance() {
        if (instance == null) instance = new ChampionTitleManager();
        return instance;
    }

    private static final NamespacedKey CHAMPION_KEY = new NamespacedKey(ServerFest.getPlugin(), "fest_champion");
    public static NamespacedKey SHOW_CROWN = new NamespacedKey(ServerFest.getPlugin(), "show_crown");
    private final Map<UUID, ScheduledTask> championTask = new HashMap<>();

    private ChampionTitleManager() {

    }

    public boolean isChampion(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int lastChampion = pdc.getOrDefault(CHAMPION_KEY, PersistentDataType.INTEGER, -1);

        return lastChampion == DataConfig.getInstance().competitiveRound();
    }

    public void grantChampionTitle(Player champion) {
        DataConfig dataConfig = DataConfig.getInstance();

        PersistentDataContainer pdc = champion.getPersistentDataContainer();
        int currentCompetitiveRound = dataConfig.competitiveRound();
        pdc.set(CHAMPION_KEY, PersistentDataType.INTEGER, currentCompetitiveRound);

        updateChampionEffect(champion);
    }

    private void updateChampionEffect(Player champion) {
        champion.playerListName(Component.text("♛ ", NamedTextColor.GOLD)
                .append(champion.displayName()));

        championTask.put(champion.getUniqueId(), champion.getScheduler().runAtFixedRate(ServerFest.getPlugin(), _ -> {
            if (!champion.isInvisible())
                showCrown(champion);
        }, () -> championTask.remove(champion.getUniqueId()), 5, 5));
    }

    private static final int POINTS = 16;
    private static final double RADIUS = 0.4;

    public static void showCrown(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        double y = base.getY() + player.getHeight() + 0.15;

        Collection<Player> receivers = world.getNearbyPlayers(player.getLocation(), 20, 20);

        boolean showCrown = player.getPersistentDataContainer().getOrDefault(SHOW_CROWN, PersistentDataType.BOOLEAN, true);
        if (!showCrown)
            receivers.remove(player);

        for (int i = 0; i < POINTS; i++) {
            double angle = 2 * Math.PI * i / POINTS;
            double x = base.getX() + Math.cos(angle) * RADIUS;
            double z = base.getZ() + Math.sin(angle) * RADIUS;
            double spike = (i % 2 == 0) ? 0.15 : 0;

            int g = 190 + (int) (45 * Math.sin(angle));

            Particle.DUST.builder()
                    .location(new Location(world, x, y + spike, z))
                    .count(1)
                    .offset(0, 0, 0)
                    .speed(0)
                    .color(Color.fromRGB(255, g, 0), 0.8f)
                    .receivers(receivers)
                    .spawn();
        }
    }

    public void toggleShowCrown(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        boolean showCrown = pdc.getOrDefault(SHOW_CROWN, PersistentDataType.BOOLEAN, true);

        if(!isChampion(player)) {
            player.sendMessage(Component.text("Only a champion can toggle their crown!", NamedTextColor.RED));
            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        pdc.set(SHOW_CROWN, PersistentDataType.BOOLEAN, !showCrown);

        if (showCrown) {
            player.sendMessage(Component.text("Your crown is now hidden for yourself", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("You will now see your own crown again", NamedTextColor.GRAY));
        }

        player.playSound(player, Sound.BLOCK_LEVER_CLICK, 1f, 0.5f);
    }

    public void playChampionCelebration(Player champion) {
        champion.resetTitle();
        champion.sendTitlePart(TitlePart.TITLE, MiniMessage.miniMessage().deserialize("<gradient:yellow:gold>♛ Fest Champion"));
        champion.playSound(champion, Sound.MUSIC_END, 1f, 1.3f);

        Bukkit.getOnlinePlayers().forEach(player -> {
            player.sendMessage(ServerFest.CHAT_PREFIX
                    .append(Component.text(champion.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" is now the official ", NamedTextColor.GRAY))
                    .append(MiniMessage.miniMessage().deserialize("<gradient:yellow:gold>Fest Champion"))
            );

            if (!player.equals(champion))
                player.playSound(player, Sound.ENTITY_WITHER_DEATH, 1f, 0.2f);
        });

        champion.sendMessage(ServerFest.CHAT_PREFIX
                .append(Component.text("You can toggle the crown for yourself with ", NamedTextColor.GRAY))
                .append(Component.text("/fest togglecrown", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("fest togglecrown")))
        );
    }

    public void updateActiveChampion() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (!isChampion(player)) {
                player.playerListName(null);
                ScheduledTask task = championTask.getOrDefault(player.getUniqueId(), null);

                if (task != null) {
                    task.cancel();
                    championTask.remove(player.getUniqueId());
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!isChampion(player))
            return;

        updateChampionEffect(player);

        Component joinMessage = event.joinMessage();
        if (joinMessage == null)
            return;

        event.joinMessage(joinMessage.replaceText(TextReplacementConfig.builder()
                .matchLiteral(player.getName())
                .replacement(Component.text("♛ ", NamedTextColor.GOLD)
                        .append(player.name()))
                .build()));
    }

}
