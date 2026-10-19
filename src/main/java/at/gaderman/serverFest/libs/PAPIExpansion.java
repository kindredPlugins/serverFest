package at.gaderman.serverFest.libs;

import at.gaderman.serverFest.ServerFest;
import at.gaderman.serverFest.data.player.PlayerData;
import at.gaderman.serverFest.data.player.PlayerDataManager;
import at.gaderman.serverFest.data.player.PlayerDataStorage;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class PAPIExpansion extends PlaceholderExpansion {

    private final ServerFest plugin;

    public PAPIExpansion(ServerFest plugin){
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "serverFest";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if(player == null)
            return null;

        PlayerData data = PlayerDataManager.getInstance().dataOf(player.getUniqueId());

        return switch (params.toLowerCase(Locale.ROOT)){
            case PlayerDataStorage.FESTS_PARTICIPATED -> String.valueOf(data.festsParticipated());
            case PlayerDataStorage.FESTS_WON -> String.valueOf(data.festsWon());
            case PlayerDataStorage.DUELS_HELD -> String.valueOf(data.duelsHeld());
            case PlayerDataStorage.DUELS_WON -> String.valueOf(data.duelsWon());
            case PlayerDataStorage.CHAMPION_TIMES -> String.valueOf(data.championTimes());
            default -> null;
        };
    }
}
