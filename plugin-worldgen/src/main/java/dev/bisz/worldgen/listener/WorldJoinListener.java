package dev.bisz.worldgen.listener;

import dev.bisz.worldgen.WorldGenPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Sends joining players into the managed world. */
public final class WorldJoinListener implements Listener {

  private final WorldGenPlugin plugin;

  public WorldJoinListener(WorldGenPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    Bukkit
      .getScheduler()
      .runTaskLater(
        plugin,
        () -> {
          World managed = plugin.setup().ensureWorld();
          if (managed == null) return;
          if (player.getWorld().equals(managed)) return;
          player.teleport(managed.getSpawnLocation());
        },
        1L
      );
  }
}
