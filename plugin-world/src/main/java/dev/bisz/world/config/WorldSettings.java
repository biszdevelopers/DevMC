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

  private final long regenMaxAgeMillis;
  private final int regenChunksPerTick;
  private final long regenDropPinMillis;
  private final long regenEvaluateIntervalMillis;
  private final boolean regenStripOres;
  private final int oreVeinsPerChunk;
  private final int lootCachesPerChunk;
  private final double resourceThreshold;
  private final double visibilityThreshold;
  private final long visibilityHalfLifeMillis;
  private final double visibilityPlayerBump;
  private final double visibilityEditBump;
  private final double smallStructureChance;
  private final boolean structuresEnabled;
  private final boolean structuresClearOnDespawn;
  private final int monumentSpacingChunks;
  private final int monumentPadRadius;
  private final int poiCap;
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
  private final boolean simpleRespawnEnabled;
  private final long simpleRespawnMillis;
  private final int simplePlayerRadius;
  private final List<String> simpleMaterials;
  private final boolean vanillaOres;
  private final double oreExposureWeight;
  private final boolean plantsEnabled;
  private final boolean animalsEnabled;
  private final int animalsPerChunk;
  private final String storageDimension;
  private final boolean rustMapEnabled;
  private final int rustMapIslandRadius;
  private final int rustMapSeaLevel;
  private final List<String> rustMapBiomes;
  private final int rustMapStructureRadius;

  private WorldSettings(Map<String, Object> values) {
    this.regenMaxAgeMillis =
      Json.longValue(values, "regen.max_age_seconds", 7200L) * 1000L;
    this.regenChunksPerTick = Math.max(
      1,
      Json.integer(values, "regen.chunks_per_tick", 2)
    );
    this.regenDropPinMillis =
      Json.longValue(values, "regen.drop_pin_seconds", 600L) * 1000L;
    this.regenEvaluateIntervalMillis =
      Json.longValue(values, "regen.evaluate_interval_seconds", 5L) * 1000L;
    this.regenStripOres = Json.bool(values, "regen.strip_ores", true);
    this.oreVeinsPerChunk = Math.max(
      0,
      Json.integer(values, "regen.ore_veins_per_chunk", 12)
    );
    this.lootCachesPerChunk = Math.max(
      0,
      Json.integer(values, "regen.loot_caches_per_chunk", 1)
    );
    this.resourceThreshold = Json.decimal(
      values,
      "regen.resource_threshold",
      0.35
    );
    this.visibilityThreshold = Json.decimal(
      values,
      "regen.visibility_threshold",
      0.2
    );
    this.visibilityHalfLifeMillis =
      Math.max(
        1L,
        Json.longValue(values, "regen.visibility_half_life_seconds", 1800L)
      ) *
      1000L;
    this.visibilityPlayerBump = Json.decimal(
      values,
      "visibility.player_bump",
      0.5
    );
    this.visibilityEditBump = Json.decimal(
      values,
      "visibility.edit_bump",
      0.25
    );
    this.smallStructureChance = Json.decimal(
      values,
      "small_structure.chance",
      0.15
    );
    this.structuresEnabled = Json.bool(values, "structures.enabled", true);
    this.structuresClearOnDespawn = Json.bool(
      values,
      "structures.clear_on_despawn",
      true
    );
    this.monumentSpacingChunks = Math.max(
      1,
      Json.integer(values, "structures.monument_spacing_chunks", 24)
    );
    this.monumentPadRadius = Math.max(
      0,
      Json.integer(values, "structures.monument_pad_radius", 2)
    );
    this.poiCap = Math.max(0, Json.integer(values, "structures.poi_cap", 64));
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
    this.setupWorldName = Json.string(values, "setup.world_name", "");
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
      Json.integer(values, "pregen.chunks_per_tick", 20)
    );
    this.simpleRespawnEnabled = Json.bool(
      values,
      "regen.simple_respawn_enabled",
      true
    );
    this.simpleRespawnMillis =
      Math.max(
        1L,
        Json.longValue(values, "regen.simple_respawn_seconds", 30L)
      ) *
      1000L;
    this.simplePlayerRadius = Math.max(
      0,
      Json.integer(values, "regen.simple_player_radius", 8)
    );
    List<String> configuredMaterials = Json.stringList(
      values,
      "regen.simple_materials"
    );
    this.simpleMaterials = configuredMaterials.isEmpty()
      ? DEFAULT_SIMPLE_MATERIALS
      : configuredMaterials;
    this.vanillaOres = Json.bool(values, "regen.vanilla_ores", false);
    this.oreExposureWeight = Json.decimal(
      values,
      "ore.exposure_weight",
      0.7
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
    this.rustMapEnabled = Json.bool(
      values,
      "worldgen.rustmap.enabled",
      true
    );
    this.rustMapIslandRadius = Math.max(
      64,
      Json.integer(values, "worldgen.rustmap.island_radius", 1500)
    );
    this.rustMapSeaLevel = Json.integer(
      values,
      "worldgen.rustmap.sea_level",
      62
    );
    List<String> configuredBiomes = Json.stringList(
      values,
      "worldgen.rustmap.biomes"
    );
    this.rustMapBiomes = configuredBiomes.isEmpty()
      ? DEFAULT_RUSTMAP_BIOMES
      : configuredBiomes;
    this.rustMapStructureRadius = Math.max(
      64,
      Json.integer(values, "worldgen.rustmap.structure_radius", 1100)
    );
  }

  /** Basic blocks restored quickly before the slower ore cycle. */
  private static final List<String> DEFAULT_SIMPLE_MATERIALS = List.of(
    "stone",
    "deepslate",
    "dirt",
    "grass_block",
    "sand",
    "red_sand",
    "gravel",
    "clay",
    "sandstone",
    "tuff",
    "andesite",
    "diorite",
    "granite",
    "calcite",
    "netherrack",
    "end_stone",
    "snow_block",
    "obsidian"
  );

  /** One biome per angular sector of the rust-map island. */
  private static final List<String> DEFAULT_RUSTMAP_BIOMES = List.of(
    "plains",
    "forest",
    "desert",
    "savanna",
    "taiga",
    "jungle",
    "swamp",
    "snowy_plains"
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

  public long regenMaxAgeMillis() {
    return regenMaxAgeMillis;
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

  public boolean stripOres() {
    return regenStripOres;
  }

  public int oreVeinsPerChunk() {
    return oreVeinsPerChunk;
  }

  public int lootCachesPerChunk() {
    return lootCachesPerChunk;
  }

  /** Resource fraction at or below which a chunk is considered depleted. */
  public double resourceThreshold() {
    return resourceThreshold;
  }

  /** Visibility at or below which a region is considered idle. */
  public double visibilityThreshold() {
    return visibilityThreshold;
  }

  public long visibilityHalfLifeMillis() {
    return visibilityHalfLifeMillis;
  }

  public double visibilityPlayerBump() {
    return visibilityPlayerBump;
  }

  public double visibilityEditBump() {
    return visibilityEditBump;
  }

  /** Chance per chunk that a destructible small structure is placed. */
  public double smallStructureChance() {
    return smallStructureChance;
  }

  /** Whether custom structure pasting and events are enabled. */
  public boolean structuresEnabled() {
    return structuresEnabled;
  }

  /** Whether despawned structures have their blocks cleared. */
  public boolean structuresClearOnDespawn() {
    return structuresClearOnDespawn;
  }

  /** Minimum spacing between monuments, in chunks. */
  public int monumentSpacingChunks() {
    return monumentSpacingChunks;
  }

  /** Radius cleared/flattened around a monument footprint. */
  public int monumentPadRadius() {
    return monumentPadRadius;
  }

  /** Maximum number of live POI structures. */
  public int poiCap() {
    return poiCap;
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

  /** Whether basic blocks are quickly restored after being mined. */
  public boolean simpleRespawnEnabled() {
    return simpleRespawnEnabled;
  }

  public long simpleRespawnMillis() {
    return simpleRespawnMillis;
  }

  /** Blocks a player must be clear of for a simple block to respawn. */
  public int simplePlayerRadius() {
    return simplePlayerRadius;
  }

  /** Lower-case material names handled by the quick respawn layer. */
  public List<String> simpleMaterials() {
    return simpleMaterials;
  }

  /** Whether vanilla ore generation is expected; false means we strip it. */
  public boolean vanillaOres() {
    return vanillaOres;
  }

  /** Probability that an ore vein must be air-exposed; fewer buried veins. */
  public double oreExposureWeight() {
    return oreExposureWeight;
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

  public boolean rustMapEnabled() {
    return rustMapEnabled;
  }

  public int rustMapIslandRadius() {
    return rustMapIslandRadius;
  }

  public int rustMapSeaLevel() {
    return rustMapSeaLevel;
  }

  /** Biomes assigned one per angular sector of the island. */
  public List<String> rustMapBiomes() {
    return rustMapBiomes;
  }

  /** Radius at which the single copy of each structure is anchored. */
  public int rustMapStructureRadius() {
    return rustMapStructureRadius;
  }
}
