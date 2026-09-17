package dev.bisz.city.config;

import dev.bisz.bundler.JSON;
import dev.bisz.city.storage.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

/** Runtime-tunable settings for the city plugin. */
public final class CitySettings {

  /** ServerData-relative settings document. */
  public static final String FILE = "city/settings.json";

  private final long regenMaxAgeMillis;
  private final int regenChunksPerTick;
  private final long regenDropPinMillis;
  private final long regenEvaluateIntervalMillis;
  private final boolean regenStripOres;
  private final int oreVeinsPerChunk;
  private final int lootCachesPerChunk;
  private final int regionSize;
  private final double resourceThreshold;
  private final double visibilityThreshold;
  private final long visibilityHalfLifeMillis;
  private final double visibilityPlayerBump;
  private final double visibilityEditBump;
  private final double smallStructureChance;
  private final boolean structuresEnabled;
  private final boolean structuresClearOnDespawn;
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
  private final boolean setupSpawnCity;
  private final String setupCityName;
  private final String setupDifficulty;
  private final Map<String, String> setupGamerules;
  private final int pregenRadiusChunks;
  private final int pregenChunksPerTick;

  private CitySettings(Map<String, Object> values) {
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
      Json.integer(values, "regen.ore_veins_per_chunk", 8)
    );
    this.lootCachesPerChunk = Math.max(
      0,
      Json.integer(values, "regen.loot_caches_per_chunk", 1)
    );
    this.regionSize = Math.max(
      1,
      Json.integer(values, "region.size", 4)
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
      true
    );
    this.setupBorderSize = Json.decimal(values, "setup.border_size", 0.0);
    this.setupSpawnCity = Json.bool(values, "setup.spawn_city", true);
    this.setupCityName = Json.string(values, "setup.city_name", "spawn");
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
  }

  /** Installs defaults into ServerData and loads the settings document. */
  public static CitySettings load(JavaPlugin plugin) {
    try (
      InputStream defaults = plugin.getResource("city/settings.json")
    ) {
      if (defaults == null) {
        throw new IllegalStateException(
          "Missing bundled city settings defaults"
        );
      }
      JSON.mergeMissingDefaults(FILE, defaults);
    } catch (IOException exception) {
      throw new IllegalStateException(
        "Cannot close bundled city settings defaults",
        exception
      );
    }
    return new CitySettings(JSON.loadDataFromDataBase(FILE));
  }

  /** Creates settings from an in-memory map, for tests. */
  public static CitySettings from(Map<String, Object> values) {
    return new CitySettings(values);
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

  /** Chunks per region axis. */
  public int regionSize() {
    return regionSize;
  }

  /** Resource fraction at or below which a region is considered depleted. */
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

  /** Worlds where city and wilderness rules apply; empty means every world. */
  public List<String> managedWorlds() {
    return managedWorlds;
  }

  /** Whether the supplied world is governed by city rules. */
  public boolean managesWorld(String worldName) {
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

  public boolean setupSpawnCity() {
    return setupSpawnCity;
  }

  public String setupCityName() {
    return setupCityName;
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
}
