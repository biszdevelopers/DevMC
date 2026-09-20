package dev.bisz.world.setup;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.ChunkKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Generates or regenerates a square of chunks around a center.
 *
 * <p>Generation loads each chunk <b>asynchronously</b> (so the heavy vanilla
 * terrain work never blocks the main thread) and then applies features on the
 * main thread, keeping a small number of chunks in flight. Regeneration resets
 * terrain and re-applies features synchronously, bounded per tick.
 */
public final class AreaRegenerator {

  private enum Mode {
    GENERATE,
    REGENERATE,
  }

  private static final int MAX_IN_FLIGHT = 1;

  private final WorldPlugin plugin;
  private final int inFlightLimit;
  private final Deque<ChunkKey> queue = new ArrayDeque<>();
  private final Set<String> inFlight = new HashSet<>();
  private BukkitTask task;
  private World world;
  private Mode mode;
  private int total;
  private int done;
  private CommandSender notifier;

  public AreaRegenerator(WorldPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.inFlightLimit = Math.min(
      MAX_IN_FLIGHT,
      Math.max(1, plugin.settings().pregenChunksPerTick())
    );
  }

  /** Whether a pass is running. */
  public boolean running() {
    return task != null;
  }

  /** Chunks processed so far in the current pass. */
  public int done() {
    return done;
  }

  /** Total chunks in the current pass. */
  public int total() {
    return total;
  }

  /** Generates a square of chunks (terrain + features) around a center. */
  public boolean startGenerate(
    World world,
    Location center,
    int radiusChunks,
    CommandSender notifier
  ) {
    return start(world, center, radiusChunks, notifier, Mode.GENERATE);
  }

  /** Regenerates a square of chunks (terrain reset + features) around a center. */
  public boolean startRegenerate(
    World world,
    Location center,
    int radiusChunks,
    CommandSender notifier
  ) {
    return start(world, center, radiusChunks, notifier, Mode.REGENERATE);
  }

  private boolean start(
    World world,
    Location center,
    int radiusChunks,
    CommandSender notifier,
    Mode mode
  ) {
    Objects.requireNonNull(world, "world");
    stop();
    this.world = world;
    this.mode = mode;
    this.notifier = notifier;
    this.done = 0;
    Location origin = center == null ? world.getSpawnLocation() : center;
    int centerX = origin.getBlockX() >> 4;
    int centerZ = origin.getBlockZ() >> 4;
    int radius = Math.max(0, radiusChunks);
    // Nearest chunks first, so the area around the centre is usable while the
    // rest of the map fills in.
    List<ChunkKey> keys = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        keys.add(new ChunkKey(world.getName(), centerX + dx, centerZ + dz));
      }
    }
    keys.sort(
      Comparator.comparingLong(key -> {
        long dx = key.x() - centerX;
        long dz = key.z() - centerZ;
        return dx * dx + dz * dz;
      })
    );
    queue.addAll(keys);
    this.total = queue.size();
    plugin
      .getLogger()
      .info(
        (mode == Mode.GENERATE ? "Generating " : "Regenerating ") +
        total +
        " chunks around " +
        centerX +
        "," +
        centerZ +
        " in " +
        world.getName() +
        "."
      );
    this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    return true;
  }

  /** Stops the current pass, if any. */
  public void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    queue.clear();
    inFlight.clear();
    world = null;
    notifier = null;
    done = 0;
    total = 0;
  }

  private void tick() {
    if (world == null) {
      stop();
      return;
    }
    if (mode == Mode.GENERATE) {
      tickGenerate();
    } else {
      tickRegenerate();
    }
  }

  /** Loads chunks off-thread, applying features on the main thread. */
  private void tickGenerate() {
    while (inFlight.size() < inFlightLimit && !queue.isEmpty()) {
      ChunkKey key = queue.poll();
      inFlight.add(key.encode());
      world
        .getChunkAtAsync(key.x(), key.z())
        .thenAccept(chunk ->
          Bukkit
            .getScheduler()
            .runTask(plugin, () -> {
              try {
                if (world != null) {
                  plugin.regenerationScheduler().generateChunk(world, key);
                }
              } finally {
                inFlight.remove(key.encode());
                done++;
                if (queue.isEmpty() && inFlight.isEmpty()) finish();
              }
            })
        );
    }
  }

  private void tickRegenerate() {
    long started = System.currentTimeMillis();
    long budget = plugin.settings().regenBudgetMillisPerTick();
    while (!queue.isEmpty()) {
      if (System.currentTimeMillis() - started >= budget) break;
      plugin.regenerationScheduler().forceChunk(queue.poll());
      done++;
    }
    if (queue.isEmpty()) finish();
  }

  private void finish() {
    if (task == null) return;
    int generated = done;
    CommandSender target = notifier;
    Mode finished = mode;
    stop();
    plugin
      .getLogger()
      .info(
        (finished == Mode.GENERATE ? "Generation" : "Regeneration") +
        " complete: " +
        generated +
        " chunks."
      );
    notifyDone(target, finished, generated);
  }

  private void notifyDone(CommandSender target, Mode finished, int generated) {
    if (target == null) return;
    String verb = finished == Mode.GENERATE ? "generated" : "regenerated";
    if (target instanceof Player player) {
      player.sendMessage("§a" + generated + " chunks " + verb + ".");
    } else {
      target.sendMessage(generated + " chunks " + verb + ".");
    }
  }
}
