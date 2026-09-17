package dev.bisz.city.setup;

import dev.bisz.city.config.CitySettings;
import dev.bisz.players.locales.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Generates and saves chunks in a square around the world spawn within a
 * per-tick budget, so a fresh world is ready before players arrive.
 */
public final class ChunkPregenerator {

  private final JavaPlugin plugin;
  private final CitySettings settings;
  private BukkitTask task;
  private World world;
  private int radius;
  private int side;
  private int index;
  private int total;
  private int centerX;
  private int centerZ;
  private CommandSender notifier;

  public ChunkPregenerator(JavaPlugin plugin, CitySettings settings) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** Whether a pre-generation pass is running. */
  public boolean running() {
    return task != null;
  }

  /** Chunks processed so far in the current pass. */
  public int done() {
    return index;
  }

  /** Total chunks in the current pass. */
  public int total() {
    return total;
  }

  /**
   * Starts generating a square of chunks around the world spawn.
   *
   * @param notifier optional sender messaged on completion
   */
  public boolean start(World world, int radiusChunks, CommandSender notifier) {
    Objects.requireNonNull(world, "world");
    stop();
    this.world = world;
    this.radius = Math.max(1, radiusChunks);
    this.side = radius * 2 + 1;
    this.total = side * side;
    this.index = 0;
    this.centerX = world.getSpawnLocation().getBlockX() >> 4;
    this.centerZ = world.getSpawnLocation().getBlockZ() >> 4;
    this.notifier = notifier;
    this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    return true;
  }

  /** Stops the current pass, if any. */
  public void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    world = null;
    notifier = null;
    index = 0;
    total = 0;
  }

  private void tick() {
    if (world == null) {
      stop();
      return;
    }
    int budget = settings.pregenChunksPerTick();
    while (budget-- > 0 && index < total) {
      int offsetX = index / side - radius;
      int offsetZ = index % side - radius;
      int chunkX = centerX + offsetX;
      int chunkZ = centerZ + offsetZ;
      world.getChunkAt(chunkX, chunkZ);
      world.unloadChunk(chunkX, chunkZ, true);
      index++;
    }
    if (index >= total) {
      int generated = total;
      CommandSender target = notifier;
      stop();
      notifyDone(target, generated);
    }
  }

  private void notifyDone(CommandSender target, int generated) {
    if (target == null) return;
    if (target instanceof Player player) {
      player.sendMessage(Locale.get(player, "city.pregen.done", generated));
    } else {
      target.sendMessage(
        "Pre-generation complete: " + generated + " chunks."
      );
    }
  }
}
