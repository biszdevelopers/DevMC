package dev.bisz.world.listener;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.Plot;
import dev.bisz.world.model.PlotState;
import dev.bisz.players.locales.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Periodically expires overdue rent and refills monument loot. */
public final class RentScheduler {

  private final WorldPlugin plugin;
  private BukkitTask task;

  public RentScheduler(WorldPlugin plugin) {
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
    for (Settlement settlement : plugin.settlements().all()) {
      for (Plot plot : plugin.plots().storedPlots(settlement)) {
        if (plot.state() != PlotState.RENTED) continue;
        if (plot.rentPaidUntil() > now || plot.owner() == null) continue;
        Player owner = Bukkit.getPlayer(plot.owner());
        if (owner != null) {
          owner.sendMessage(Locale.get(owner, "settlement.rent.expired"));
        }
      }
    }
    plugin.plots().expireDue(now);
    plugin.monuments().tick(now);
    plugin.police().prune();
  }
}
