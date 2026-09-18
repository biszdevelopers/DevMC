package dev.bisz.world.setup;

import dev.bisz.world.WorldPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Prompts joining players to complete world setup while it is incomplete. */
public final class SetupListener implements Listener {

  private final WorldPlugin plugin;

  public SetupListener(WorldPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Bukkit
      .getScheduler()
      .runTaskLater(
        plugin,
        () -> plugin.setupDialogs().promptOnJoin(event.getPlayer()),
        20L
      );
  }
}
