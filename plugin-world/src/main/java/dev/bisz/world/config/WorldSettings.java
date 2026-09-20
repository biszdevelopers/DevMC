package dev.bisz.world.config;

import dev.bisz.bundler.JSON;
import dev.bisz.world.storage.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

/** Runtime-tunable settings for the settlement plugin. */
public final class WorldSettings {

  /** ServerData-relative settings document. */
  public static final String FILE = "world/settings.json";

  private final long regenCycleMillis;
  private final double regenCycleJitter;
  private final long regenGraceMillis;
  private final int regenDepletionNodes;
  private final int regenChunksPerTick;
  private final long regenDropPinMillis;
  private final long regenEvaluateIntervalMillis;
  private final long regenDirtyInactivityMillis;
  private final long regenBudgetMillisPerTick;
  private final boolean regenStripOres;
  private final int oreVeinsPerChunk;
  private final int lootCachesPerChunk;
  private final double smallStructureChance;
  private final boolean structuresEnabled;
  private final int poiCap;
  private final int regenPlayerRadiusChunks;
  private final long rentDefaultPrice;
  private final long rentDefaultPeriodMillis;
  private final int plotSize;
  private final long dailySellQuota;
  private final long policeWantedMillis;
  private final List<String> managedWorlds;
  private final String setupWorldName;
  private final String setupEnvironment;
  private final String setupWorldType;
  private final String setupSeed;
  private final boolean setupGenerateStructures;
  private final double setupBorderSize;
  private final boolean setupSpawnSettlement;
  private final String setupSettlementName;
  private final String setupDifficulty;
  private final Map<String, String> setupGamerules;
  private final int pregenRadiusChunks;
  private final int pregenChunksPerTick;
  private final boolean fastRegenEnabled;
  private final long fastRegenMillis;
  private final boolean regenVerifyChanges;
  private final boolean vanillaOres;
  private final double oreCountMultiplier;
  private final double oreExposureWeight;
  private final double terrainAmplitude;
  private final boolean terrainCaves;
  private final double terrainCaveScale;
  private final double terrainCaveThreshold;
  private final boolean plantsEnabled;
  private final boolean animalsEnabled;
  private final int animalsPerChunk;
  private final String storageDimension;
  private final boolean debugVerifyRegen;
  private final boolean islandEnabled;
  private final int islandSize;
  private final double islandCoastFraction;
  private final int islandOceanMargin;
  private final int islandSeaLevel;
  private final List<String> worldBiomes;
  private final double climateSweep;
  private final double climateScale;
  private final double climateWarp;
  private final int climateOctaves;

  private WorldSettings(Map<String, Object> values) {
    this.regenCycleMillis =
      Math.max(
        1L,
        Json.longValue(values, "regen.cycle_seconds", 3600L)
      ) *
      1000L;
    this.regenCycleJitter = Math.max(
      0.0,
      Json.decimal(values, "regen.cycle_jitter", 0.1)
    );
    this.regenGraceMillis =
      Math.max(
        0L,
        Json.longValue(values, "regen.grace_seconds", 60L)
      ) *
      1000L;
    this.regenDepletionNodes = Math.max(
      1,
      Json.integer(values, "regen.depletion_nodes", 48)
    );
    this.regenChunksPerTick = Math.max(
      1,
      Json.integer(values, "regen.chunks_per_tick", 2)
    );
    this.regenDropPinMillis =
      Json.longValue(values, "regen.drop_pin_seconds", 600L) * 1000L;
    this.regenEvaluateIntervalMillis =
      Json.longValue(values, "regen.evaluate_interval_seconds", 5L) * 1000L;
    this.regenDirtyInactivityMillis =
      Math.max(
        0L,
        Json.longValue(values, "regen.dirty_inactivity_seconds", 60L)
      ) *
      1000L;
    this.regenBudgetMillisPerTick = Math.max(
      1L,
      Json.longValue(values, "regen.budget_millis_per_tick", 25L)
    );
    this.regenStripOres = Json.bool(values, "regen.strip_ores", true);
    this.oreVeinsPerChunk = Math.max(
      0,
      Json.integer(values, "regen.ore_veins_per_chunk", 12)
    );
    this.lootCachesPerChunk = Math.max(
      0,
      Json.integer(values, "regen.loot_caches_per_chunk", 1)
    );
    this.smallStructureChance = Json.decimal(
      values,
      "small_structure.chance",
      0.15
    );
    this.structuresEnabled = Json.bool(values, "structures.enabled", true);
    this.poiCap = Math.max(0, Json.integer(values, "structures.poi_cap", 64));
    this.regenPlayerRadiusChunks = Math.max(
      0,
      Json.integer(values, "regen.player_radius_chunks", 4)
    );
    this.rentDefaultPrice = Math.max(
      0L,
      Json.longValue(values, "rent.default_price", 500L)
    );
    this.rentDefaultPeriodMillis = Math.max(
      1L,
      Json.longValue(values, "rent.default_period_seconds", 86_400L) * 1000L
    );
    this.plotSize = Json.integer(values, "plot.size", 16);
    this.dailySellQuota = Math.max(
      0L,
      Json.longValue(values, "economy.daily_sell_quota", 10_000L)
    );
    this.policeWantedMillis = Math.max(
      1L,
      Json.longValue(values, "police.wanted_seconds", 600L) * 1000L
    );
    this.managedWorlds = Json.stringList(values, "worlds");
    this.setupWorldName = Json.string(values, "setup.world_name", "devmc");
    this.setupEnvironment = Json.string(values, "setup.environment", "NORMAL");
    this.setupWorldType = Json.string(values, "setup.world_type", "NORMAL");
    this.setupSeed = Json.string(values, "setup.seed", "");
    this.setupGenerateStructures = Json.bool(
      values,
      "setup.generate_structures",
      false
    );
    this.setupBorderSize = Json.decimal(values, "setup.border_size", 0.0);
    this.setupSpawnSettlement = Json.bool(values, "setup.spawn_settlement", true);
    this.setupSettlementName = Json.string(values, "setup.settlement_name", "spawn");
    this.setupDifficulty = Json.string(values, "setup.difficulty", "NORMAL");
    Map<String, String> rules = new LinkedHashMap<>();
    String rulePrefix = "setup.gamerule.";
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      if (entry.getKey().startsWith(rulePrefix)) {
        rules.put(
          entry.getKey().substring(rulePrefix.length()),
          String.valueOf(entry.getValue())
        );
      }
    }
    this.setupGamerules = Map.copyOf(rules);
    this.pregenRadiusChunks = Math.max(
      0,
      Json.integer(values, "pregen.radius_chunks", 0)
    );
    this.pregenChunksPerTick = Math.max(
      1,
      Json.integer(values, "pregen.chunks_per_tick", 2)
    );
    this.fastRegenEnabled = Json.bool(
      values,
      "regen.fast_regen_enabled",
      true
    );
    this.fastRegenMillis =
      Math.max(
        0L,
        Json.longValue(values, "regen.fast_regen_seconds", 30L)
      ) *
      1000L;
    this.regenVerifyChanges = Json.bool(
      values,
      "regen.verify_changes",
      true
    );
    this.vanillaOres = Json.bool(values, "regen.vanilla_ores", false);
    this.oreCountMultiplier = Json.decimal(
      values,
      "ore.count_multiplier",
      1.0
    );
    this.oreExposureWeight = Json.decimal(
      values,
      "ore.exposure_weight",
      0.25
    );
    this.terrainAmplitude = Math.max(
      1.0,
      Json.decimal(values, "worldgen.terrain.amplitude", 1.15)
    );
    this.terrainCaves = Json.bool(values, "worldgen.terrain.caves", true);
    this.terrainCaveScale = Json.decimal(
      values,
      "worldgen.terrain.cave_scale",
      0.06
    );
    this.terrainCaveThreshold = Json.decimal(
      values,
      "worldgen.terrain.cave_threshold",
      0.62
    );
    this.plantsEnabled = Json.bool(values, "features.plants.enabled", true);
    this.animalsEnabled = Json.bool(values, "features.animals.enabled", true);
    this.animalsPerChunk = Math.max(
      0,
      Json.integer(values, "features.animals_per_chunk", 2)
    );
    this.storageDimension = Json.string(
      values,
      "storage.dimension",
      "world_admin"
    );
    this.debugVerifyRegen = Json.bool(
      values,
      "debug.verify_regen",
      false
    );
    this.islandEnabled = Json.bool(values, "worldgen.island.enabled", true);
    this.islandSize = Math.max(
      256,
      Json.integer(values, "worldgen.island.size", 2048)
    );
    this.islandCoastFraction = Json.decimal(
      values,
      "worldgen.island.coast_fraction",
      0.15
    );
    this.islandOceanMargin = Math.max(
      0,
      Json.integer(values, "worldgen.island.ocean_margin", 96)
    );
    this.islandSeaLevel = Json.integer(
      values,
      "worldgen.island.sea_level",
      63
    );
    List<String> configuredBiomes = Json.stringList(values, "worldgen.biomes");
    this.worldBiomes = configuredBiomes.isEmpty()
      ? DEFAULT_WORLD_BIOMES
      : configuredBiomes;
    this.climateSweep = Json.decimal(
      values,
      "worldgen.climate.sweep",
      0.6
    );
    this.climateScale = Json.decimal(
      values,
      "worldgen.climate.scale",
      0.0016
    );
    this.climateWarp = Json.decimal(
      values,
      "worldgen.climate.warp",
      120.0
    );
    this.climateOctaves = Math.max(
      1,
      Json.integer(values, "worldgen.climate.octaves", 4)
    );
  }

  /**
   * Biomes the island generator guarantees. Badlands and a mountain biome are
   * required so the gold and emerald ore filters have somewhere to apply.
   */
  private static final List<String> DEFAULT_WORLD_BIOMES = List.of(
    "plains",
    "forest",
    "birch_forest",
    "taiga",
    "snowy_plains",
    "desert",
    "savanna",
    "jungle",
    "swamp",
    "badlands",
    "windswept_hills",
    "stony_peaks"
  );

  /** Installs defaults into ServerData and loads the settings document. */
  public static WorldSettings load(JavaPlugin plugin) {
    try (
      InputStream defaults = plugin.getResource("world/settings.json")
    ) {
      if (defaults == null) {
        throw new IllegalStateException(
          "Missing bundled settlement settings defaults"
        );
      }
      JSON.mergeMissingDefaults(FILE, defaults);
    } catch (IOException exception) {
      throw new IllegalStateException(
        "Cannot close bundled settlement settings defaults",
        exception
      );
    }
    return new WorldSettings(JSON.loadDataFromDataBase(FILE));
  }

  /** Creates settings from an in-memory map, for tests. */
  public static WorldSettings from(Map<String, Object> values) {
    return new WorldSettings(values);
  }

  /** How long after being farmed a chunk waits before it may reset. */
  public long regenCycleMillis() {
    return regenCycleMillis;
  }

  /** Deterministic per-chunk jitter as a fraction of the cycle. */
  public double regenCycleJitter() {
    return regenCycleJitter;
  }

  /** How long after the last presence/edit a scheduled reset must wait. */
  public long regenGraceMillis() {
    return regenGraceMillis;
  }

  /** Resource nodes extracted before a chunk is scheduled for a reset. */
  public int regenDepletionNodes() {
    return regenDepletionNodes;
  }

  public int regenChunksPerTick() {
    return regenChunksPerTick;
  }

  public long regenDropPinMillis() {
    return regenDropPinMillis;
  }

  public long regenEvaluateIntervalMillis() {
    return regenEvaluateIntervalMillis;
  }

  /** How long a dirty chunk must be idle before it is reset. */
  public long regenDirtyInactivityMillis() {
    return regenDirtyInactivityMillis;
  }

  /** Wall-clock budget for regeneration work per server tick. */
  public long regenBudgetMillisPerTick() {
    return regenBudgetMillisPerTick;
  }

  public boolean stripOres() {
    return regenStripOres;
  }

  public int oreVeinsPerChunk() {
    return oreVeinsPerChunk;
  }

  public int lootCachesPerChunk() {
    return lootCachesPerChunk;
  }

  /** Chance per chunk that a destructible small structure is placed. */
  public double smallStructureChance() {
    return smallStructureChance;
  }

  /** Whether custom structure pasting and events are enabled. */
  public boolean structuresEnabled() {
    return structuresEnabled;
  }

  /** Maximum number of live POI structures. */
  public int poiCap() {
    return poiCap;
  }

  /** Chunks around a chunk that must be free of players before it regenerates. */
  public int regenPlayerRadiusChunks() {
    return regenPlayerRadiusChunks;
  }

  public long rentDefaultPrice() {
    return rentDefaultPrice;
  }

  public long rentDefaultPeriodMillis() {
    return rentDefaultPeriodMillis;
  }

  public int plotSize() {
    return plotSize;
  }

  public long dailySellQuota() {
    return dailySellQuota;
  }

  public long policeWantedMillis() {
    return policeWantedMillis;
  }

  /** Worlds where settlement and wilderness rules apply; empty means every world. */
  public List<String> managedWorlds() {
    return managedWorlds;
  }

  /** Whether the supplied world is governed by settlement rules. */
  public boolean managesWorld(String worldName) {
    if (worldName != null && worldName.equals(storageDimension)) return false;
    if (managedWorlds.isEmpty()) return true;
    return managedWorlds.contains(worldName);
  }

  /** World created or adopted by the setup procedure; blank uses the caller's. */
  public String setupWorldName() {
    return setupWorldName;
  }

  public String setupEnvironment() {
    return setupEnvironment;
  }

  public String setupWorldType() {
    return setupWorldType;
  }

  public String setupSeed() {
    return setupSeed;
  }

  public boolean setupGenerateStructures() {
    return setupGenerateStructures;
  }

  /** World border diameter applied by setup; zero leaves the border unchanged. */
  public double setupBorderSize() {
    return setupBorderSize;
  }

  public boolean setupSpawnSettlement() {
    return setupSpawnSettlement;
  }

  public String setupSettlementName() {
    return setupSettlementName;
  }

  public String setupDifficulty() {
    return setupDifficulty;
  }

  /** Gamerules applied by setup, keyed by gamerule name. */
  public Map<String, String> setupGamerules() {
    return setupGamerules;
  }

  public int pregenRadiusChunks() {
    return pregenRadiusChunks;
  }

  public int pregenChunksPerTick() {
    return pregenChunksPerTick;
  }

  /** Whether the fast terrain-fill pass runs on dirty chunks. */
  public boolean fastRegenEnabled() {
    return fastRegenEnabled;
  }

  /** How long after a change the fast terrain-fill pass may run. */
  public long fastRegenMillis() {
    return fastRegenMillis;
  }

  /** Whether unload-time terrain sampling backstops the change events. */
  public boolean regenVerifyChanges() {
    return regenVerifyChanges;
  }

  /** Whether vanilla ore generation is expected; false means we strip it. */
  public boolean vanillaOres() {
    return vanillaOres;
  }

  /** Multiplier applied to vanilla ore vein counts; slightly more ore. */
  public double oreCountMultiplier() {
    return oreCountMultiplier;
  }

  /** Probability of skipping a vein that is not exposed to air. */
  public double oreExposureWeight() {
    return oreExposureWeight;
  }

  /** Vertical terrain scale above sea level (1.0 = vanilla). */
  public double terrainAmplitude() {
    return terrainAmplitude;
  }

  /** Whether extra caves are carved on top of vanilla caves. */
  public boolean terrainCaves() {
    return terrainCaves;
  }

  /** Noise frequency for the extra cave carver. */
  public double terrainCaveScale() {
    return terrainCaveScale;
  }

  /** Noise threshold for the extra cave carver; higher = fewer caves. */
  public double terrainCaveThreshold() {
    return terrainCaveThreshold;
  }

  /** Whether biome vegetation is generated as a feature. */
  public boolean plantsEnabled() {
    return plantsEnabled;
  }

  /** Whether passive animals are generated as a feature. */
  public boolean animalsEnabled() {
    return animalsEnabled;
  }

  /** Passive animals attempted per chunk. */
  public int animalsPerChunk() {
    return animalsPerChunk;
  }

  /** World used to store and inspect structures and simulations. */
  public String storageDimension() {
    return storageDimension;
  }

  /** Whether the one-shot regeneration self-test runs at startup. */
  public boolean debugVerifyRegen() {
    return debugVerifyRegen;
  }

  /** Whether the managed world uses the island generator. */
  public boolean islandEnabled() {
    return islandEnabled;
  }

  /** The map diameter in blocks; the island radius is half of this. */
  public int islandSize() {
    return islandSize;
  }

  /** Fraction of the island radius used for the coastal compression band. */
  public double islandCoastFraction() {
    return islandCoastFraction;
  }

  /** Ocean blocks kept between the coast and the world border. */
  public int islandOceanMargin() {
    return islandOceanMargin;
  }

  public int islandSeaLevel() {
    return islandSeaLevel;
  }

  /** Biomes the island generator guarantees to place. */
  public List<String> worldBiomes() {
    return worldBiomes;
  }

  /** Climate sweep amplitude: how far the map spans the climate range. */
  public double climateSweep() {
    return climateSweep;
  }

  /** Base frequency of the climate fractal noise (smaller = larger biomes). */
  public double climateScale() {
    return climateScale;
  }

  /** Domain warp strength applied before sampling the climate, in blocks. */
  public double climateWarp() {
    return climateWarp;
  }

  /** Number of fractal octaves in the climate field. */
  public int climateOctaves() {
    return climateOctaves;
  }
}
