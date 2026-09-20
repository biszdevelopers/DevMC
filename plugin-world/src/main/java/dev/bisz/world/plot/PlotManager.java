package dev.bisz.world.plot;

import dev.bisz.bundler.JSON;
import dev.bisz.world.settlement.SettlementManager;
import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.integration.CurrencyGateway;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.Plot;
import dev.bisz.world.model.PlotId;
import dev.bisz.world.model.PlotState;
import dev.bisz.world.storage.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns plot ownership, rent charging, expiry, and takeover. */
public final class PlotManager {

  /** ServerData-relative plot document. */
  public static final String FILE = "world/plots.json";

  private final JavaPlugin plugin;
  private final WorldSettings settings;
  private final SettlementManager settlements;
  private final CurrencyGateway currency;
  private final Map<String, Plot> plots = new LinkedHashMap<>();

  public PlotManager(
    JavaPlugin plugin,
    WorldSettings settings,
    SettlementManager settlements,
    CurrencyGateway currency
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.settlements = Objects.requireNonNull(settlements, "settlements");
    this.currency = Objects.requireNonNull(currency, "currency");
  }

  /** Loads all plots from ServerData. */
  public void load() {
    plots.clear();
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    for (Map<String, Object> raw : Json.mapList(document, "plots")) {
      try {
        Plot plot = Plot.fromMap(raw);
        plots.put(plot.id().encode(), plot);
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning("Skipping malformed plot entry: " + exception.getMessage());
      }
    }
  }

  /** Persists all plots to ServerData. */
  public void save() {
    List<Map<String, Object>> encoded = new ArrayList<>();
    for (Plot plot : plots.values()) encoded.add(plot.toMap());
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schema", 1);
    document.put("plots", encoded);
    JSON.saveDataFromDataBase(FILE, document);
  }

  /** Returns the stored plot, or null when it has never been created. */
  public Plot get(PlotId id) {
    return plots.get(id.encode());
  }

  /** Returns the plot at a location, or null when there is none. */
  public Plot plotAt(Settlement settlement, Location location) {
    if (settlement == null || location == null) return null;
    PlotId id = PlotGrid.plotAt(
      settlement,
      location.getBlockX(),
      location.getBlockZ(),
      0
    );
    return get(id);
  }

  /** Every plot cell in a settlement, existing or not. */
  public List<PlotId> plotIds(Settlement settlement) {
    return PlotGrid.plotIds(settlement);
  }

  /** Every stored plot belonging to a settlement. */
  public Collection<Plot> storedPlots(Settlement settlement) {
    List<Plot> result = new ArrayList<>();
    for (Plot plot : plots.values()) {
      if (plot.settlementId().equals(settlement.id())) result.add(plot);
    }
    return List.copyOf(result);
  }

  /** Whether a plot is open to a new renter. */
  public boolean isClaimable(Plot plot) {
    if (plot == null) return true;
    return (
      plot.state() == PlotState.VACANT ||
      plot.state() == PlotState.DERELICT ||
      plot.state() == PlotState.RAIDABLE
    );
  }

  /**
   * Claims a plot for a player, charging one rent period.
   *
   * @return the claimed plot, or null when the plot is unavailable or the
   *     player cannot afford the rent.
   */
  public Plot claim(Settlement settlement, PlotId id, UUID playerId) {
    Objects.requireNonNull(settlement, "settlement");
    Objects.requireNonNull(playerId, "playerId");
    Plot plot = plots.computeIfAbsent(
      id.encode(),
      ignored -> PlotGrid.vacant(settlement, id)
    );
    if (!isClaimable(plot)) return null;
    long price = settlement.rentPrice();
    if (!currency.withdraw(playerId, price, "settlement.plot_rent")) return null;
    long now = System.currentTimeMillis();
    plot.owner(playerId);
    plot.rentPaidUntil(now + settlement.rentPeriodMillis());
    plot.state(PlotState.RENTED);
    save();
    return plot;
  }

  /** Extends a plot's paid-through time, charging one rent period. */
  public boolean renew(Plot plot, UUID playerId) {
    Objects.requireNonNull(plot, "plot");
    Objects.requireNonNull(playerId, "playerId");
    Settlement settlement = settlements.get(plot.settlementId());
    if (settlement == null) return false;
    if (!playerId.equals(plot.owner())) return false;
    long price = settlement.rentPrice();
    if (!currency.withdraw(playerId, price, "settlement.plot_renew")) return false;
    long base = Math.max(System.currentTimeMillis(), plot.rentPaidUntil());
    plot.rentPaidUntil(base + settlement.rentPeriodMillis());
    plot.state(PlotState.RENTED);
    save();
    return true;
  }

  /** Releases a plot back to the settlement, keeping its contents. */
  public boolean release(Plot plot, UUID playerId) {
    Objects.requireNonNull(plot, "plot");
    if (plot.owner() == null || !plot.owner().equals(playerId)) return false;
    plot.owner(null);
    plot.rentPaidUntil(0L);
    plot.state(PlotState.VACANT);
    save();
    return true;
  }

  /**
   * Expires every overdue plot. Protection drops and the plot becomes
   * raidable and claimable; contents are untouched.
   *
   * @return the plots that expired in this pass.
   */
  public List<Plot> expireDue(long now) {
    List<Plot> expired = new ArrayList<>();
    for (Plot plot : plots.values()) {
      if (plot.state() != PlotState.RENTED) continue;
      if (plot.rentPaidUntil() > now) continue;
      plot.owner(null);
      plot.rentPaidUntil(0L);
      plot.state(PlotState.RAIDABLE);
      expired.add(plot);
    }
    if (!expired.isEmpty()) save();
    return expired;
  }

  /** Marks a raidable plot as looted and derelict. */
  public void markDerelict(Plot plot) {
    if (plot == null || plot.state() != PlotState.RAIDABLE) return;
    plot.state(PlotState.DERELICT);
    save();
  }

  /** The default rent period from settings. */
  public long defaultPeriodMillis() {
    return settings.rentDefaultPeriodMillis();
  }
}
