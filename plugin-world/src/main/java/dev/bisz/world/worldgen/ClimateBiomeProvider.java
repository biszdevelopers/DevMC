package dev.bisz.world.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeParameterPoint;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

/**
 * Places the configured biomes from the vanilla climate. Biome borders are
 * contours of a noisy climate field ({@link ClimateField}), so they are organic
 * rather than straight. Erosion is left vanilla, so mountain biomes follow real
 * terrain.
 */
public final class ClimateBiomeProvider extends BiomeProvider {

  private static final double OCEAN_CONTINENTALNESS = -0.11;
  private static final double DEEP_OCEAN_CONTINENTALNESS = -0.45;
  private static final double CARVED_OCEAN_FALLOFF = 0.12;

  private final IslandMask mask;
  private final BiomeClimateTable table;
  private final double sweep;
  private final double scale;
  private final double warpBlocks;
  private final int octaves;
  private final List<Biome> biomes;
  private final Biome ocean = Biome.OCEAN;
  private final Biome deepOcean = Biome.DEEP_OCEAN;

  private volatile ClimateField field;
  private volatile long fieldSeed = Long.MIN_VALUE;

  public ClimateBiomeProvider(
    IslandMask mask,
    List<String> biomeKeys,
    double sweep,
    double scale,
    double warpBlocks,
    int octaves
  ) {
    this.mask = Objects.requireNonNull(mask, "mask");
    this.table = new BiomeClimateTable(biomeKeys);
    this.sweep = sweep;
    this.scale = Math.max(0.00001, scale);
    this.warpBlocks = warpBlocks;
    this.octaves = Math.max(1, octaves);
    Set<Biome> resolved = new LinkedHashSet<>();
    for (String key : Objects.requireNonNull(biomeKeys, "biomeKeys")) {
      Biome biome = resolve(key);
      if (biome != null) resolved.add(biome);
    }
    resolved.add(Biome.PLAINS);
    this.biomes = List.copyOf(new ArrayList<>(resolved));
  }

  @Override
  public Biome getBiome(WorldInfo info, int x, int y, int z) {
    return getBiome(info, x, y, z, null);
  }

  @Override
  public Biome getBiome(
    WorldInfo info,
    int x,
    int y,
    int z,
    BiomeParameterPoint point
  ) {
    return biomeAt(
      info.getSeed(),
      x,
      z,
      point == null ? 0.0 : point.getContinentalness(),
      point == null ? 0.0 : point.getDepth(),
      point == null ? 0.0 : point.getTemperature(),
      point == null ? 0.0 : point.getHumidity(),
      point == null ? 0.0 : point.getErosion()
    );
  }

  /**
   * The biome at a column for raw climate inputs. Exposed so placement can be
   * tested and diagnosed without a live world.
   */
  public Biome biomeAt(
    long seed,
    int x,
    int z,
    double continentalness,
    double depth,
    double temperature,
    double humidity,
    double erosion
  ) {
    if (mask.falloff(x, z) <= CARVED_OCEAN_FALLOFF) return ocean;
    if (continentalness <= DEEP_OCEAN_CONTINENTALNESS) return deepOcean;
    if (continentalness <= OCEAN_CONTINENTALNESS) return ocean;

    ClimateField climate = field(seed);
    Biome biome = resolve(
      table.nearest(
        climate.temperature(x, z, temperature),
        climate.humidity(x, z, humidity),
        erosion
      )
    );
    return biome == null ? Biome.PLAINS : biome;
  }

  @Override
  public List<Biome> getBiomes(WorldInfo info) {
    return configuredBiomes();
  }

  /** Every biome this provider may return, without needing a {@link WorldInfo}. */
  public List<Biome> configuredBiomes() {
    List<Biome> all = new ArrayList<>(biomes);
    if (!all.contains(ocean)) all.add(ocean);
    if (!all.contains(deepOcean)) all.add(deepOcean);
    return List.copyOf(all);
  }

  private ClimateField field(long seed) {
    ClimateField current = field;
    if (current != null && fieldSeed == seed) return current;
    synchronized (this) {
      if (field == null || fieldSeed != seed) {
        field = new ClimateField(
          seed,
          mask.islandRadius(),
          sweep,
          scale,
          warpBlocks,
          octaves
        );
        fieldSeed = seed;
      }
      return field;
    }
  }

  private static Biome resolve(String key) {
    if (key == null) return null;
    return Registry.BIOME.get(
      NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT))
    );
  }
}
