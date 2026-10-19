package at.gaderman.serverFest.data.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manages all player statistics as {@link PlayerData} objects. Exposes write methods which it then directs to the
 * underlying storages. This class does not know how the PlayerData is saved but is the central interface to reach out to.
 */
public class PlayerDataManager implements Listener {

    private static final PlayerDataManager INSTANCE = new PlayerDataManager();

    public static PlayerDataManager getInstance() {
        return INSTANCE;
    }

    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    private PlayerDataManager() {
    }

    public PlayerData dataOfPlayer(Player player) {
        return dataOf(player.getUniqueId());
    }

    public PlayerData dataOf(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, PlayerDataStorage.getInstance()::readPlayerData);
    }

    private void update(Player player, Consumer<PlayerData> action) {
        UUID uuid = player.getUniqueId();
        PlayerData data = dataOf(uuid);

        action.accept(data);
        PlayerDataStorage.getInstance().savePlayerData(uuid, data);
    }

    public void addFestsParticipated(Player player) {
        update(player, data -> data.setFestsParticipated(data.festsParticipated() + 1));
    }

    public void addFestsWon(Player player) {
        update(player, data -> data.setFestsWon(data.festsWon() + 1));
    }

    public void addDuelsHeld(Player player) {
        update(player, data -> data.setDuelsHeld(data.duelsHeld() + 1));
    }

    public void addDuelsWon(Player player) {
        update(player, data -> data.setDuelsWon(data.duelsWon() + 1));
    }

    public void addChampionTimes(Player player) {
        update(player, data -> data.setChampionTimes(data.championTimes() + 1));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        dataOf(event.getPlayer().getUniqueId());
    }
}