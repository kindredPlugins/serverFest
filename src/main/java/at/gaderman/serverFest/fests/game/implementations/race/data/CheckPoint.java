package at.gaderman.serverFest.fests.game.implementations.race.data;

import org.bukkit.Location;

/**
 * Used to better store checkpoint information for RaceGames
 */
public record CheckPoint(Location location, double width, double height) {}
