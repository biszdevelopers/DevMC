package dev.bisz.city.listener;

import dev.bisz.city.CityPlugin;
import dev.bisz.city.model.City;
import dev.bisz.city.model.Plot;
import dev.bisz.city.model.PlotState;
import dev.bisz.players.locales.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Periodically expires overdue rent and refills monument loot. */
public final class RentScheduler {

  private final CityPlugin plugin;
  private BukkitTask task;

  public RentScheduler(CityPlugin plugin) {
    this.plugin = plugin;
  }

  /** Starts the minute-long maintenance loop. */
  public void start() {
    if (task != null) return;
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 600L, 1200L);
  }

  /** Stops the maintenance loop. */
  public void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  private void tick() {
    long now = System.currentTimeMillis();
    for (City city : plugin.cities().all()) {
      for (Plot plot : plugin.plots().storedPlots(city)) {
        if (plot.state() != PlotState.RENTED) continue;
        if (plot.rentPaidUntil() > now || plot.owner() == null) continue;
        Player owner = Bukkit.getPlayer(plot.owner());
        if (owner != null) {
          owner.sendMessage(Locale.get(owner, "city.rent.expired"));
        }
      }
    }
    plugin.plots().expireDue(now);
    plugin.monuments().tick(now);
    plugin.structures().tick(now);
    plugin.police().prune();
  }
}
