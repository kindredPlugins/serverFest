package at.gaderman.serverFest.fests.game.implementations.race.arena;

import at.gaderman.serverFest.fests.arenas.ArenaLayout;
import at.gaderman.serverFest.fests.game.implementations.race.data.CheckPoint;

import java.util.List;

public interface RaceGameArenaLayout extends ArenaLayout {
    List<CheckPoint> checkPoints();
}
