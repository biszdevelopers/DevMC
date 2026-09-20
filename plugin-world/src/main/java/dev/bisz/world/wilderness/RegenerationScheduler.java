package dev.bisz.world.wilderness;

import dev.bisz.world.settlement.SettlementManager;
import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.feature.FeatureGenerator;
import dev.bisz.world.model.ChunkKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Drives automatic wilderness regeneration.
 *
 * <p>Untouched chunks are never regenerated. A chunk that has been changed
 * (player or non-player) is <b>dirty-regenerated</b>: fully reset to barebone
 * terrain plus features once no non-spectator player is within
 * {@code regen.player_radius_chunks} and {@code regen.fast_regen_seconds} have
 * elapsed. A chunk that was farmed (enough resource nodes extracted) is also
 * scheduled for a reset {@code regen.cycle_seconds} later, with a deterministic
 * jitter so chunks farmed together do not reset together. Either reset waits
 * until the chunk is due, idle, past the grace period, and unpinned.
 *
 * <p>Work is bounded by both a per-tick chunk cap and a wall-clock budget, so the
 * reset stream is smooth and can never stall the server.
 */
public final class RegenerationScheduler {

  private final JavaPlugin plugin;
  private final WorldSettings settings;
  private final ChunkIndicators indicators;
  private final ChunkRegenerator regenerator;
  private final FeatureGenerator features;
  private final SettlementManager settlements;
  private final MonumentManager monuments;

  private final Deque<ChunkKey> queue = new ArrayDeque<>();
  private final Set<String> queuedChunks = new HashSet<>();
  private final Deque<ChunkKey> fastQueue = new ArrayDeque<>();
  private final Set<String> fastQueued = new HashSet<>();

  /** A runtime override for the cycle, for testing; 0 uses the setting. */
  private volatile long cycleOverrideMillis = 0L;

  private BukkitTask processTask;
  private BukkitTask evaluateTask;

  public RegenerationScheduler(
    JavaPlugin plugin,
    WorldSettings settings,
    ChunkIndicators indicators,
    ChunkRegenerator regenerator,
    FeatureGenerator features,
    SettlementManager settlements,
    MonumentManager monuments
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.indicators = Objects.requireNonNull(indicators, "indicators");
    this.regenerator = Objects.requireNonNull(regenerator, "regenerator");
    this.features = Objects.requireNonNull(features, "features");
    this.settlements = Objects.requireNonNull(settlements, "settlements");
    this.monuments = Objects.requireNonNull(monuments, "monuments");
  }

  /** Starts the evaluation and processing loops. */
  public void start() {
    if (processTask != null) return;
    long evaluateTicks = Math.max(
      20L,
      settings.regenEvaluateIntervalMillis() / 50L
    );
    processTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processQueue, 1L, 1L);
    evaluateTask = Bukkit.getScheduler().runTaskTimer(
      plugin,
      this::evaluate,
      evaluateTicks,
      evaluateTicks
    );
  }

  /** Stops both loops. */
  public void stop() {
    if (processTask != null) {
      processTask.cancel();
      processTask = null;
    }
    if (evaluateTask != null) {
      evaluateTask.cancel();
      evaluateTask = null;
    }
  }

  /** Chunks currently queued or being regenerated (full or fast). */
  public int pendingChunks() {
    return queuedChunks.size() + fastQueued.size();
  }

  /** The cycle in use: the runtime override when set, else the setting. */
  public long effectiveCycleMillis() {
    long override = cycleOverrideMillis;
    return override > 0L ? override : settings.regenCycleMillis();
  }

  /**
   * Overrides the reset cycle at runtime, for testing. Pass {@code 0} to clear
   * the override and use {@code regen.cycle_seconds}. Already-scheduled chunks
   * are pulled in to the new cycle.
   *
   * @return how many scheduled chunks were rescheduled
   */
  public int setCycleOverride(long millis) {
    this.cycleOverrideMillis = Math.max(0L, millis);
    long cycle = effectiveCycleMillis();
    long now = System.currentTimeMillis();
    int rescheduled = 0;
    for (ChunkState state : indicators.all()) {
      if (!state.isScheduled()) continue;
      state.forceSchedule(now + cycle + jitter(state.key(), cycle));
      rescheduled++;
    }
    return rescheduled;
  }

  /** A human-readable summary of what automatic regeneration is tracking. */
  public String status() {
    long now = System.currentTimeMillis();
    int tracked = 0;
    int farmed = 0;
    int scheduled = 0;
    int due = 0;
    long nextDue = Long.MAX_VALUE;
    for (ChunkState state : indicators.all()) {
      tracked++;
      if (state.extractedNodes() > 0 || state.dirty()) farmed++;
      if (!state.isScheduled()) continue;
      scheduled++;
      if (state.isDue(now)) due++;
      nextDue = Math.min(nextDue, state.dueAt());
    }
    String next = nextDue == Long.MAX_VALUE
      ? "none"
      : formatDuration(Math.max(0L, nextDue - now));
    return (
      "cycle=" +
      formatDuration(effectiveCycleMillis()) +
      (cycleOverrideMillis > 0L ? "(test)" : "") +
      ", " +
      tracked +
      " tracked, " +
      farmed +
      " farmed, " +
      scheduled +
      " scheduled, " +
      due +
      " due, " +
      pendingChunks() +
      " queued, next " +
      next
    );
  }

  /** A one-line status for a single chunk, or {@code null} when untracked. */
  public String chunkStatus(ChunkKey key) {
    ChunkState state = indicators.get(key);
    if (state == null) return null;
    long now = System.currentTimeMillis();
    StringBuilder line = new StringBuilder();
    line
      .append("extracted=")
      .append(state.extractedNodes())
      .append('/')
      .append(settings.regenDepletionNodes())
      .append(" resource=")
      .append(String.format(Locale.ROOT, "%.2f", state.resource()));
    if (state.dirty()) line.append(" dirty");
    if (state.isPinned(now)) line.append(" pinned");
    if (state.isScheduled()) {
      line
        .append(" -> regen in ")
        .append(formatDuration(Math.max(0L, state.dueAt() - now)));
    } else {
      line.append(" -> not scheduled");
    }
    return line.toString();
  }

  /** The next few chunks to reset, soonest first. */
  public List<String> nextDue(int limit) {
    long now = System.currentTimeMillis();
    List<ChunkState> scheduled = new ArrayList<>();
    for (ChunkState state : indicators.all()) {
      if (state.isScheduled()) scheduled.add(state);
    }
    scheduled.sort(Comparator.comparingLong(ChunkState::dueAt));
    List<String> lines = new ArrayList<>();
    for (ChunkState state : scheduled) {
      if (lines.size() >= limit) break;
      long remaining = Math.max(0L, state.dueAt() - now);
      lines.add(
        state.key().encode() +
        " in " +
        formatDuration(remaining) +
        " (extracted=" +
        state.extractedNodes() +
        (state.dirty() ? ", dirty" : "") +
        ")"
      );
    }
    if (lines.isEmpty()) lines.add("nothing scheduled");
    return lines;
  }

  private void evaluate() {
    long now = System.currentTimeMillis();
    markPresence(now);
    Set<String> occupied = occupiedChunks();
    long cycle = effectiveCycleMillis();
    for (ChunkState state : indicators.all()) {
      ChunkKey key = state.key();
      if (queuedChunks.contains(key.encode())) continue;
      World world = Bukkit.getWorld(key.world());
      if (world == null) continue;
      if (state.baselineNodes() <= 0) {
        state.ensureBaseline(features.estimateBaseline(world));
      }
      if (
        RegenerationPolicy.shouldSchedule(
          state,
          now,
          settings.regenDepletionNodes(),
          settings.regenDirtyInactivityMillis()
        )
      ) {
        state.schedule(now + cycle + jitter(key, cycle));
        plugin
          .getLogger()
          .info(
            "Auto-regen scheduled " +
            key.encode() +
            " (extracted=" +
            state.extractedNodes() +
            (state.dirty() ? ", dirty" : "") +
            ")"
          );
      }
      RegenerationPolicy.Decision decision = RegenerationPolicy.evaluate(
        state,
        now,
        occupied.contains(key.encode()),
        settings.regenGraceMillis()
      );
      if (decision.reset()) enqueueChunk(state);
      if (
        settings.fastRegenEnabled() &&
        !fastQueued.contains(key.encode()) &&
        RegenerationPolicy.shouldFastRegen(
          state,
          now,
          occupied.contains(key.encode()),
          settings.fastRegenMillis()
        )
      ) {
        fastQueued.add(key.encode());
        fastQueue.add(key);
      }
    }
  }

  private void markPresence(long now) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.getGameMode() == GameMode.SPECTATOR) continue;
      Location location = player.getLocation();
      World world = location.getWorld();
      if (world == null || !settlements.isManagedWorld(world.getName())) continue;
      ChunkKey chunk = ChunkKey.of(location);
      if (settlements.isSettlement(chunk)) continue;
      ChunkState state = indicators.getOrCreate(chunk, now);
      if (state.baselineNodes() <= 0) {
        state.ensureBaseline(features.estimateBaseline(world));
      }
      state.markPresence(now);
    }
  }

  /**
   * Every chunk within the idle radius of a non-spectator player, encoded once
   * per evaluation so the per-chunk check is a set lookup instead of an
   * O(chunks x players) scan.
   */
  private Set<String> occupiedChunks() {
    int radius = settings.regenPlayerRadiusChunks();
    Set<String> occupied = new HashSet<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.getGameMode() == GameMode.SPECTATOR) continue;
      Location location = player.getLocation();
      World world = location.getWorld();
      if (world == null || !settlements.isManagedWorld(world.getName())) continue;
      int centerX = location.getBlockX() >> 4;
      int centerZ = location.getBlockZ() >> 4;
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dz = -radius; dz <= radius; dz++) {
          occupied.add(
            new ChunkKey(world.getName(), centerX + dx, centerZ + dz).encode()
          );
        }
      }
    }
    return occupied;
  }

  private void enqueueChunk(ChunkState state) {
    ChunkKey chunk = state.key();
    if (settlements.isSettlement(chunk) || monuments.isExempt(chunk)) {
      indicators.remove(chunk);
      return;
    }
    if (queuedChunks.add(chunk.encode())) {
      // A full reset supersedes any pending fast fill.
      if (fastQueued.remove(chunk.encode())) fastQueue.remove(chunk);
      queue.add(chunk);
      plugin.getLogger().info("Auto-regen queued " + chunk.encode());
    }
  }

  private void processQueue() {
    if (queue.isEmpty() && fastQueue.isEmpty()) return;
    long now = System.currentTimeMillis();
    int maxChunks = settings.regenChunksPerTick();
    long budgetMillis = settings.regenBudgetMillisPerTick();
    Set<String> occupied = occupiedChunks();
    int processed = 0;
    while (processed < maxChunks && !queue.isEmpty()) {
      if (processed > 0 && System.currentTimeMillis() - now >= budgetMillis) {
        break;
      }
      ChunkKey key = queue.poll();
      queuedChunks.remove(key.encode());
      World world = Bukkit.getWorld(key.world());
      if (world == null) continue;
      ChunkState state = indicators.getOrCreate(key, now);
      if (
        occupied.contains(key.encode()) ||
        state.isPinned(now) ||
        now - state.lastActivityAt() < settings.regenGraceMillis()
      ) {
        // Not safe to reset yet: re-queue instead of dropping it.
        if (queuedChunks.add(key.encode())) queue.add(key);
        processed++;
        continue;
      }
      world.getChunkAt(key.x(), key.z());
      int nodes = regenerateChunk(world, key, now);
      if (nodes >= 0) {
        state.markRegenerated(now, nodes);
        plugin
          .getLogger()
          .info("Auto-regen complete " + key.encode() + " (" + nodes + " nodes)");
      }
      processed++;
    }
    processFastQueue(now, budgetMillis, occupied);
  }

  /**
   * The dirty-regen path: a changed chunk is fully regenerated (terrain plus
   * features) once no non-spectator player is nearby and the fast delay has
   * elapsed. This is the same barebone + feature pass as the scheduled reset,
   * just triggered by a change instead of the resource cycle.
   */
  private void processFastQueue(
    long now,
    long budgetMillis,
    Set<String> occupied
  ) {
    if (fastQueue.isEmpty()) return;
    if (!settings.fastRegenEnabled()) {
      fastQueue.clear();
      fastQueued.clear();
      return;
    }
    long started = System.currentTimeMillis();
    while (!fastQueue.isEmpty()) {
      if (System.currentTimeMillis() - started >= budgetMillis) break;
      ChunkKey key = fastQueue.poll();
      fastQueued.remove(key.encode());
      World world = Bukkit.getWorld(key.world());
      if (world == null) continue;
      ChunkState state = indicators.getOrCreate(key, now);
      if (occupied.contains(key.encode()) || state.isPinned(now)) {
        // Leave it out of the queue; the evaluate loop re-adds it when safe.
        continue;
      }
      world.getChunkAt(key.x(), key.z());
      int nodes = regenerateChunk(world, key, now);
      if (nodes >= 0) {
        state.markRegenerated(now, nodes);
        plugin
          .getLogger()
          .info("Dirty-regen complete " + key.encode() + " (" + nodes + " nodes)");
      }
    }
  }

  /**
   * Restores terrain and re-applies features.
   *
   * @return the number of resource nodes placed, or {@code -1} on failure
   */
  private int regenerateChunk(World world, ChunkKey key, long now) {
    try {
      boolean terrain = regenerator.regenerate(world, key.x(), key.z());
      if (!terrain) {
        plugin
          .getLogger()
          .warning(
            "Terrain regeneration reported no change for " +
            key.encode() +
            "; applying features anyway."
          );
      }
      // The scratch world is generated without decorations, so there are no
      // natural ores to strip unless a vanilla-ore world is configured.
      if (settings.stripOres() && settings.vanillaOres()) {
        regenerator.stripOres(world, key.x(), key.z());
      }
      Random random = new Random(seedFor(world, key) ^ (now & 0xFFFFL));
      return features.apply(world, key.x(), key.z(), random);
    } catch (Throwable failure) {
      plugin
        .getLogger()
        .warning(
          "Failed to regenerate chunk " +
          key.encode() +
          ": " +
          failure.getMessage()
        );
      return -1;
    }
  }

  /** Immediately regenerates a single chunk, ignoring its schedule. */
  public boolean forceChunk(ChunkKey key) {
    World world = Bukkit.getWorld(key.world());
    if (world == null) return false;
    world.getChunkAt(key.x(), key.z());
    long now = System.currentTimeMillis();
    int nodes = regenerateChunk(world, key, now);
    if (nodes < 0) return false;
    indicators.getOrCreate(key, now).markRegenerated(now, nodes);
    return true;
  }

  /** Generates a chunk and applies features without resetting terrain. */
  public void generateChunk(World world, ChunkKey key) {
    world.getChunkAt(key.x(), key.z());
    long now = System.currentTimeMillis();
    Random random = new Random(seedFor(world, key) ^ (now & 0xFFFFL));
    int nodes = features.apply(world, key.x(), key.z(), random);
    indicators.getOrCreate(key, now).markRegenerated(now, nodes);
  }

  /**
   * A deterministic per-chunk offset so chunks farmed at the same time do not
   * all reset together. In {@code [0, cycle * cycle_jitter]}.
   */
  private long jitter(ChunkKey key, long cycle) {
    double fraction = Math.max(0.0, settings.regenCycleJitter());
    long range = (long) (cycle * fraction);
    if (range <= 0L) return 0L;
    long hash = key.world().hashCode() * 341873128712L;
    hash ^= key.x() * 132897987541L;
    hash ^= key.z() * 42317861L;
    hash ^= hash >>> 33;
    return Math.floorMod(hash, range);
  }

  private static String formatDuration(long millis) {
    long minutes = millis / 60_000L;
    if (minutes <= 0L) return (millis / 1000L) + "s";
    if (minutes < 60L) return minutes + "m";
    return (minutes / 60L) + "h" + (minutes % 60L) + "m";
  }

  private static long seedFor(World world, ChunkKey key) {
    long seed = world.getSeed();
    seed ^= key.x() * 341873128712L;
    seed ^= key.z() * 132897987541L;
    return seed;
  }
}
