package dev.bisz.world.plot;

import dev.bisz.world.settlement.SettlementManager;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.Plot;
import dev.bisz.world.model.PlotState;
import dev.bisz.world.model.ZoneType;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Decides whether a player may modify or access land. */
public final class ProtectionService {

  private final SettlementManager settlements;
  private final PlotManager plots;

  public ProtectionService(SettlementManager settlements, PlotManager plots) {
    this.settlements = Objects.requireNonNull(settlements, "settlements");
    this.plots = Objects.requireNonNull(plots, "plots");
  }

  /**
   * Whether a player may place or break a block at a location.
   *
   * <p>Wilderness edits are permitted and later erased by regeneration. Settlement
   * edits require plot ownership, an open raidable plot, or admin rights.
   */
  public boolean canBuild(Player player, Location location) {
    ZoneType zone = settlements.zoneAt(location);
    if (zone == null || zone == ZoneType.WILDERNESS) return true;
    if (player.hasPermission("world.admin")) return true;
    return canUsePlot(player, location);
  }

  /** Whether a player may open a container at a location. */
  public boolean canOpenContainer(Player player, Location location) {
    ZoneType zone = settlements.zoneAt(location);
    if (zone == null || zone == ZoneType.WILDERNESS) return true;
    if (player.hasPermission("world.admin")) return true;
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

  /** Whether a settlement plot is open to everyone because rent lapsed. */
  public boolean isOpen(Plot plot) {
    return (
      plot != null &&
      (plot.state() == PlotState.RAIDABLE ||
        plot.state() == PlotState.DERELICT)
    );
  }

  private boolean canUsePlot(Player player, Location location) {
    Settlement settlement = settlements.settlementAt(location);
    if (settlement == null) return false;
    Plot plot = plots.plotAt(settlement, location);
    if (plot == null) return false;
    if (isOpen(plot)) return true;
    return ownsPlot(player, plot);
  }
}
