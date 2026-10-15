package at.gaderman.serverFest.fests.game.interfaces;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface BossBarPlayerData extends FestPlayerData {
    UUID uuid();

    BossBar bossBar();

    BossBar setBossBar(BossBar bossBar);

    default void updateBossBar(Player viewer, Component name, float progress, BossBar.Color color) {
        BossBar bossBar = bossBar();
        if (bossBar == null) {
            bossBar = BossBar.bossBar(name, progress, color, BossBar.Overlay.PROGRESS);
            setBossBar(bossBar);
            bossBar.addViewer(viewer);
        }

        bossBar.name(name);
        bossBar.progress(Math.clamp(progress, 0, 1));
    }

    @Override
    default void reset() {
        Player player = Bukkit.getPlayer(uuid());
        if (player != null && bossBar() != null)
            bossBar().removeViewer(player);
    }
}
