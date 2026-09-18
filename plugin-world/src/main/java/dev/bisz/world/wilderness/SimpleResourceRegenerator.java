package dev.bisz.world.wilderness;

import dev.bisz.world.config.WorldSettings;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The quick layer of wilderness regeneration: basic blocks mined by players are
 * restored after a short delay, as long as no player is standing nearby and the
 * spot is still empty. Ore veins are handled later by the region scheduler.
 */
public final class SimpleResourceRegenerator {

  private static final long PLAYER_RETRY_MILLIS = 5_000L;
  private static final long PLACED_MAX_AGE_MILLIS = 1_800_000L;

  private final JavaPlugin plugin;
  private final WorldSettings settings;
  private final SimpleResourceTracker tracker = new SimpleResourceTracker();
  private BukkitTask task;

  public SimpleResourceRegenerator(JavaPlugin plugin, WorldSettings settings) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** Starts the one-second restoration loop. */
  public void start() {
    if (task != null || !settings.simpleRespawnEnabled()) return;
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
  }

  /** Stops the restoration loop. */
  public void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  /** The shared tracker, also used by the edit listener. */
  public SimpleResourceTracker tracker() {
    return tracker;
  }

  /** Whether a material is handled by the quick layer. */
  public boolean isSimple(Material material) {
    return settings.simpleMaterials().contains(
      material.getKey().getKey().toLowerCase(java.util.Locale.ROOT)
    );
  }

  /** Delay before a mined block is restored. */
  public long respawnDelayMillis() {
    return settings.simpleRespawnMillis();
  }

  /** Schedules restoration of a mined block, unless a player placed it. */
  public void recordMined(Block block) {
    if (!settings.simpleRespawnEnabled() || !isSimple(block.getType())) return;
    if (tracker.isPlayerPlaced(block)) {
      tracker.forget(block);
      return;
    }
    tracker.record(block, System.currentTimeMillis() + respawnDelayMillis());
  }

  /** Remembers a player-placed block so the quick layer leaves it alone. */
  public void markPlaced(Block block) {
    if (!settings.simpleRespawnEnabled() || !isSimple(block.getType())) return;
    tracker.markPlaced(block, System.currentTimeMillis());
  }

  private void tick() {
    long now = System.currentTimeMillis();
    tracker.prunePlaced(now, PLACED_MAX_AGE_MILLIS);
    List<SimpleResourceTracker.Respawn> due = tracker.due(now);
    if (due.isEmpty()) return;
    for (SimpleResourceTracker.Respawn entry : due) {
      World world = Bukkit.getWorld(entry.world());
      if (world == null) {
        tracker.complete(entry.key());
        continue;
      }
      Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
      if (!block.getType().isAir() && !block.isReplaceable()) {
        tracker.complete(entry.key());
        continue;
      }
      if (playerNear(world, entry.x(), entry.y(), entry.z())) {
        tracker.postpone(entry.key(), now, PLAYER_RETRY_MILLIS);
        continue;
      }
      block.setBlockData(entry.data(), false);
      tracker.complete(entry.key());
    }
  }

  private boolean playerNear(World world, int x, int y, int z) {
    int radius = settings.simplePlayerRadius();
    if (radius <= 0) return false;
    double squared = (double) radius * radius;
    for (Player player : world.getPlayers()) {
      double dx = player.getLocation().getX() - (x + 0.5);
      double dy = player.getLocation().getY() - (y + 0.5);
      double dz = player.getLocation().getZ() - (z + 0.5);
      if (dx * dx + dy * dy + dz * dz <= squared) return true;
    }
    return false;
  }
}
