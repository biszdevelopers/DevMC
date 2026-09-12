/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package dev.bisz.uptime;

import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.uptime.ServerHealth;
import dev.bisz.uptime.ServerHealthService;
import java.util.Set;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ServerHealthModule
  implements BundlerModule, ServerHealthService {

  private volatile ServerHealth health = new ServerHealth(20.0, 0L);
  private BukkitTask task;
  private long previous = System.nanoTime();

  @Override
  public String id() {
    return "server-health";
  }

  @Override
  public Set<String> dependencies() {
    return Set.of();
  }

  @Override
  public void start(ModuleContext context) {
    this.task = context
      .plugin()
      .getServer()
      .getScheduler()
      .runTaskTimer(
        (Plugin) context.plugin(),
        () -> {
          long current = System.nanoTime();
          long delay = Math.max(0L, (current - this.previous) / 1000000L - 50L);
          this.previous = current;
          this.health = new ServerHealth(
            delay == 0L ? 20.0 : Math.max(0.0, 20.0 - (double) delay / 50.0),
            delay
          );
        },
        1L,
        1L
      );
    context.services().register(ServerHealthService.class, this);
  }

  @Override
  public void stop() {
    if (this.task != null) {
      this.task.cancel();
    }
  }

  @Override
  public ServerHealth current() {
    return this.health;
  }
}
