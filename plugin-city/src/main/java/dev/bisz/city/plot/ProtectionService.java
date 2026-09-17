package dev.bisz.city.plot;

import dev.bisz.city.city.CityManager;
import dev.bisz.city.model.City;
import dev.bisz.city.model.Plot;
import dev.bisz.city.model.PlotState;
import dev.bisz.city.model.ZoneType;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Decides whether a player may modify or access land. */
public final class ProtectionService {

  private final CityManager cities;
  private final PlotManager plots;

  public ProtectionService(CityManager cities, PlotManager plots) {
    this.cities = Objects.requireNonNull(cities, "cities");
    this.plots = Objects.requireNonNull(plots, "plots");
  }

  /**
   * Whether a player may place or break a block at a location.
   *
   * <p>Wilderness edits are permitted and later erased by regeneration. City
   * edits require plot ownership, an open raidable plot, or admin rights.
   */
  public boolean canBuild(Player player, Location location) {
    ZoneType zone = cities.zoneAt(location);
    if (zone == null || zone == ZoneType.WILDERNESS) return true;
    if (player.hasPermission("city.admin")) return true;
    return canUsePlot(player, location);
  }

  /** Whether a player may open a container at a location. */
  public boolean canOpenContainer(Player player, Location location) {
    ZoneType zone = cities.zoneAt(location);
    if (zone == null || zone == ZoneType.WILDERNESS) return true;
    if (player.hasPermission("city.admin")) return true;
    return canUsePlot(player, location);
  }

  /** Whether the player owns the supplied plot. */
  public boolean ownsPlot(Player player, Plot plot) {
    return (
      plot != null &&
      plot.owner() != null &&
      plot.owner().equals(player.getUniqueId())
    );
  }

  /** Whether a city plot is open to everyone because rent lapsed. */
  public boolean isOpen(Plot plot) {
    return (
      plot != null &&
      (plot.state() == PlotState.RAIDABLE ||
        plot.state() == PlotState.DERELICT)
    );
  }

  private boolean canUsePlot(Player player, Location location) {
    City city = cities.cityAt(location);
    if (city == null) return false;
    Plot plot = plots.plotAt(city, location);
    if (plot == null) return false;
    if (isOpen(plot)) return true;
    return ownsPlot(player, plot);
  }
}
