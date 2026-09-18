package dev.bisz.world.wilderness;

import dev.bisz.world.settlement.SettlementManager;
import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.feature.FeatureGenerator;
import dev.bisz.world.model.ChunkKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Drives wilderness regeneration from the per-chunk indicators.
 *
 * <p>Chunks are evaluated periodically: visibility decays, player presence
 * raises it, and a chunk is selected when it is idle and either depleted of
 * resources or past the hard age cap. Selected chunks are queued and
 * regenerated a few per tick. There is no larger grouping.
 */
public final class RegenerationScheduler {

  private static final int AVERAGE_VEIN_SIZE = 7;

  private final JavaPlugin plugin;
  private final WorldSettings settings;
  private final ChunkIndicators indicators;
  private final ChunkRegenerator regenerator;
  private final FeatureGenerator features;
  private final SettlementManager settlements;
  private final MonumentManager monuments;
  private final DirtyChunkTracker tracker;

  private final Deque<ChunkKey> queue = new ArrayDeque<>();
  private final Set<String> queuedChunks = new HashSet<>();

  private BukkitTask processTask;
  private BukkitTask evaluateTask;

  public RegenerationScheduler(
    JavaPlugin plugin,
    WorldSettings settings,
    ChunkIndicators indicators,
    ChunkRegenerator regenerator,
    FeatureGenerator features,
    SettlementManager settlements,
    MonumentManager monuments,
    DirtyChunkTracker tracker
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.indicators = Objects.requireNonNull(indicators, "indicators");
    this.regenerator = Objects.requireNonNull(regenerator, "regenerator");
    this.features = Objects.requireNonNull(features, "features");
    this.settlements = Objects.requireNonNull(settlements, "settlements");
    this.monuments = Objects.requireNonNull(monuments, "monuments");
    this.tracker = Objects.requireNonNull(tracker, "tracker");
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

  /** Chunks currently queued or being regenerated. */
  public int pendingChunks() {
    return queuedChunks.size();
  }

  /** The pin and dirty tracker shared with listeners. */
  public DirtyChunkTracker tracker() {
    return tracker;
  }

  private void evaluate() {
    long now = System.currentTimeMillis();
    bumpPlayerVisibility(now);
    for (ChunkState state : new ArrayList<>(indicators.all())) {
      state.decayVisibility(now, settings.visibilityHalfLifeMillis());
      ChunkKey key = state.key();
      if (queuedChunks.contains(key.encode())) continue;
      RegenerationPolicy.Decision decision = RegenerationPolicy.evaluate(
        state,
        now,
        settings.resourceThreshold(),
        settings.visibilityThreshold(),
        settings.regenMaxAgeMillis()
      );
      if (decision.regenerate()) enqueueChunk(state, now);
    }
  }

  private void bumpPlayerVisibility(long now) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      Location location = player.getLocation();
      World world = location.getWorld();
      if (world == null || !settlements.isManagedWorld(world.getName())) continue;
      ChunkKey chunk = ChunkKey.of(location);
      if (settlements.isSettlement(chunk)) continue;
      indicators
        .getOrCreate(chunk, now)
        .bumpVisibility(settings.visibilityPlayerBump(), now);
    }
  }

  private void enqueueChunk(ChunkState state, long now) {
    ChunkKey chunk = state.key();
    if (settlements.isSettlement(chunk) || monuments.isExempt(chunk)) {
      state.markRegenerated(now, 0);
      return;
    }
    if (queuedChunks.add(chunk.encode())) queue.add(chunk);
  }

  private void processQueue() {
    int budget = settings.regenChunksPerTick();
    long now = System.currentTimeMillis();
    while (budget > 0 && !queue.isEmpty()) {
      budget--;
      ChunkKey key = queue.poll();
      queuedChunks.remove(key.encode());
      World world = Bukkit.getWorld(key.world());
      if (world == null) continue;
      if (hasPlayer(world, key) || tracker.isPinned(key, now)) continue;
      world.getChunkAt(key.x(), key.z());
      if (regenerateChunk(world, key, now)) {
        ChunkState state = indicators.get(key);
        if (state != null) {
          state.markRegenerated(now, estimateBaseline());
        }
      }
    }
  }

  private boolean regenerateChunk(World world, ChunkKey key, long now) {
    try {
      if (!regenerator.regenerate(world, key.x(), key.z())) return false;
      if (settings.stripOres()) {
        regenerator.stripOres(world, key.x(), key.z());
      }
      Random random = new Random(seedFor(world, key) ^ (now & 0xFFFFL));
      features.apply(world, key.x(), key.z(), random);
      return true;
    } catch (Throwable failure) {
      plugin
        .getLogger()
        .warning(
          "Failed to regenerate chunk " +
          key.encode() +
          ": " +
          failure.getMessage()
        );
      return false;
    }
  }

  /** Immediately regenerates a single chunk, ignoring the indicators. */
  public boolean forceChunk(ChunkKey key) {
    World world = Bukkit.getWorld(key.world());
    if (world == null) return false;
    world.getChunkAt(key.x(), key.z());
    long now = System.currentTimeMillis();
    boolean ok = regenerateChunk(world, key, now);
    if (ok) {
      ChunkState state = indicators.get(key);
      if (state != null) {
        state.markRegenerated(now, estimateBaseline());
      }
    }
    return ok;
  }

  private int estimateBaseline() {
    int perChunk =
      settings.oreVeinsPerChunk() * AVERAGE_VEIN_SIZE +
      settings.lootCachesPerChunk() +
      (int) Math.round(settings.smallStructureChance() * 2.0);
    return Math.max(1, perChunk);
  }

  private static boolean hasPlayer(World world, ChunkKey key) {
    for (Player player : world.getPlayers()) {
      if (
        (player.getLocation().getBlockX() >> 4) == key.x() &&
        (player.getLocation().getBlockZ() >> 4) == key.z()
      ) return true;
    }
    return false;
  }

  private static long seedFor(World world, ChunkKey key) {
    long seed = world.getSeed();
    seed ^= key.x() * 341873128712L;
    seed ^= key.z() * 132897987541L;
    return seed;
  }
}
