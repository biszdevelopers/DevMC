package dev.bisz.city.wilderness;

import dev.bisz.city.city.CityManager;
import dev.bisz.city.config.CitySettings;
import dev.bisz.city.model.ChunkKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * Drives wilderness regeneration from the region indicators.
 *
 * <p>Regions are evaluated periodically: visibility decays, player presence
 * raises it, and a region is selected when it is idle and either depleted of
 * resources or past the hard age cap. Selected regions enqueue their chunks,
 * which are regenerated a few per tick.
 */
public final class RegenerationScheduler {

  private static final int AVERAGE_VEIN_SIZE = 7;

  private final JavaPlugin plugin;
  private final CitySettings settings;
  private final RegionIndicators indicators;
  private final ChunkRegenerator regenerator;
  private final OreReseeder ores;
  private final SmallStructures smallStructures;
  private final LootReseeder loot;
  private final CityManager cities;
  private final MonumentManager monuments;
  private final DirtyChunkTracker tracker;

  private final Deque<ChunkKey> queue = new ArrayDeque<>();
  private final Set<String> queuedChunks = new HashSet<>();
  private final Map<String, Integer> regionRemaining = new HashMap<>();
  private final Map<String, Integer> regionBaseline = new HashMap<>();
  private final Set<String> activeRegions = new HashSet<>();

  private BukkitTask processTask;
  private BukkitTask evaluateTask;

  public RegenerationScheduler(
    JavaPlugin plugin,
    CitySettings settings,
    RegionIndicators indicators,
    ChunkRegenerator regenerator,
    OreReseeder ores,
    SmallStructures smallStructures,
    LootReseeder loot,
    CityManager cities,
    MonumentManager monuments,
    DirtyChunkTracker tracker
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.indicators = Objects.requireNonNull(indicators, "indicators");
    this.regenerator = Objects.requireNonNull(regenerator, "regenerator");
    this.ores = Objects.requireNonNull(ores, "ores");
    this.smallStructures = Objects.requireNonNull(smallStructures, "smallStructures");
    this.loot = Objects.requireNonNull(loot, "loot");
    this.cities = Objects.requireNonNull(cities, "cities");
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

  /** Regions currently queued or being regenerated. */
  public int pendingRegions() {
    return activeRegions.size();
  }

  /** The pin and dirty tracker shared with listeners. */
  public DirtyChunkTracker tracker() {
    return tracker;
  }

  private void evaluate() {
    long now = System.currentTimeMillis();
    bumpPlayerVisibility(now);
    for (RegionState state : new ArrayList<>(indicators.all())) {
      state.decayVisibility(now, settings.visibilityHalfLifeMillis());
      if (activeRegions.contains(state.key().encode())) continue;
      RegenerationPolicy.Decision decision = RegenerationPolicy.evaluate(
        state,
        now,
        settings.resourceThreshold(),
        settings.visibilityThreshold(),
        settings.regenMaxAgeMillis()
      );
      if (decision.regenerate()) enqueueRegion(state, now);
    }
  }

  private void bumpPlayerVisibility(long now) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      Location location = player.getLocation();
      World world = location.getWorld();
      if (world == null || !cities.isManagedWorld(world.getName())) continue;
      ChunkKey chunk = ChunkKey.of(location);
      if (cities.isCity(chunk)) continue;
      RegionKey region = RegionKey.of(chunk, settings.regionSize());
      indicators
        .getOrCreate(region, now)
        .bumpVisibility(settings.visibilityPlayerBump(), now);
    }
  }

  private void enqueueRegion(RegionState state, long now) {
    RegionKey region = state.key();
    String id = region.encode();
    if (activeRegions.contains(id)) return;
    int queued = 0;
    for (ChunkKey chunk : regionChunks(region)) {
      if (cities.isCity(chunk) || monuments.isExempt(chunk)) continue;
      if (queuedChunks.add(chunk.encode())) {
        queue.add(chunk);
        queued++;
      }
    }
    if (queued == 0) {
      state.markRegenerated(now, 0);
      return;
    }
    activeRegions.add(id);
    regionRemaining.put(id, queued);
    regionBaseline.put(id, estimateBaseline(queued));
  }

  private void processQueue() {
    int budget = settings.regenChunksPerTick();
    long now = System.currentTimeMillis();
    while (budget > 0 && !queue.isEmpty()) {
      budget--;
      ChunkKey key = queue.poll();
      queuedChunks.remove(key.encode());
      RegionKey region = RegionKey.of(key, settings.regionSize());
      String id = region.encode();
      World world = Bukkit.getWorld(key.world());
      if (
        world != null &&
        !hasPlayer(world, key) &&
        !tracker.isPinned(key, now)
      ) {
        world.getChunkAt(key.x(), key.z());
        regenerateChunk(world, key, now);
      }
      int left = regionRemaining.getOrDefault(id, 1) - 1;
      if (left <= 0) {
        regionRemaining.remove(id);
        activeRegions.remove(id);
        Integer baseline = regionBaseline.remove(id);
        RegionState state = indicators.get(region);
        if (state != null) {
          state.markRegenerated(now, baseline == null ? 0 : baseline);
        }
      } else {
        regionRemaining.put(id, left);
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
      ores.reseed(world, key.x(), key.z(), random);
      smallStructures.reseed(world, key.x(), key.z(), random);
      loot.reseed(world, key.x(), key.z(), random);
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
    return regenerateChunk(world, key, System.currentTimeMillis());
  }

  /** Immediately regenerates every chunk in a region. */
  public int forceRegion(RegionKey region) {
    int regenerated = 0;
    for (ChunkKey chunk : regionChunks(region)) {
      if (forceChunk(chunk)) regenerated++;
    }
    return regenerated;
  }

  private List<ChunkKey> regionChunks(RegionKey region) {
    int size = settings.regionSize();
    List<ChunkKey> chunks = new ArrayList<>(size * size);
    int startX = region.x() * size;
    int startZ = region.z() * size;
    for (int dx = 0; dx < size; dx++) {
      for (int dz = 0; dz < size; dz++) {
        chunks.add(new ChunkKey(region.world(), startX + dx, startZ + dz));
      }
    }
    return chunks;
  }

  private int estimateBaseline(int chunks) {
    int perChunk =
      settings.oreVeinsPerChunk() * AVERAGE_VEIN_SIZE +
      settings.lootCachesPerChunk() +
      (int) Math.round(settings.smallStructureChance() * 2.0);
    return Math.max(1, perChunk * chunks);
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
