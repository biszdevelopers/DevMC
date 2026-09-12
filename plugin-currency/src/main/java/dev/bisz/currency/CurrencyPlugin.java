/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.currency;

import dev.bisz.commands.CommandRegistery;
import dev.bisz.currency.CurrencyManager;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CurrencyPlugin extends JavaPlugin implements Listener {

  private static final long ARCHIVE_INTERVAL_TICKS = 20L * 60 * 60;
  private CurrencyManager currencyManager;
  private BukkitTask archiveTask;

  public void onEnable() {
    this.getServer()
      .getPluginManager()
      .registerEvents((Listener) this, (Plugin) this);
    this.currencyManager = new CurrencyManager(this);
    this.getServer()
      .getPluginManager()
      .registerEvents(
        new CurrencyManagerListener(this, this.currencyManager),
        this
      );
    CommandRegistery.register(
      this,
      new NitsCommand(this, this.currencyManager)
    );
    CommandRegistery.register(this, new ForceArchiveCurrencyCacheCommand(this));
    scheduleNextArchive();
  }

  public void onDisable() {
    try {
      if (this.archiveTask != null) {
        this.archiveTask.cancel();
        this.archiveTask = null;
      }
      if (this.currencyManager != null) {
        this.currencyManager.flushTransactions();
      }
    } catch (RuntimeException exception) {
      this.getLogger().severe(
        "Could not save currency transaction log: " + exception.getMessage()
      );
    } finally {
      CommandRegistery.unregisterAll(this);
    }
  }

  CurrencyManager.TransactionArchive forceArchiveCurrencyCache() {
    CurrencyManager.TransactionArchive archive =
      this.currencyManager.archiveTransactions();
    scheduleNextArchive();
    return archive;
  }

  private void scheduleNextArchive() {
    if (this.archiveTask != null) this.archiveTask.cancel();
    this.archiveTask = this.getServer()
      .getScheduler()
      .runTaskLater(this, this::archiveHourly, ARCHIVE_INTERVAL_TICKS);
  }

  private void archiveHourly() {
    this.archiveTask = null;
    try {
      this.currencyManager.archiveTransactions();
    } catch (RuntimeException exception) {
      this.getLogger().severe(
        "Could not archive hourly currency transaction cache; the active cache was retained: " +
        exception.getMessage()
      );
    } finally {
      if (this.isEnabled()) scheduleNextArchive();
    }
  }
}
