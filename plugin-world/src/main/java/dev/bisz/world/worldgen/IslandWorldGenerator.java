package dev.bisz.world.worldgen;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Vanilla terrain shaped into a bounded island. The vanilla noise, surface, and
 * cave stages all run; our only terrain edit is the coastal {@link IslandMask}
 * applied in the surface hook, and our only biome edit is the
 * {@link ClimateBiomeProvider}. Decorations, structures, and mobs are disabled
 * so the plugin's feature pass places its own.
 */
public final class IslandWorldGenerator extends ChunkGenerator {

  private final IslandMask mask;
  private final ClimateBiomeProvider provider;
  private final int seaLevel;
  private final int seaFloor;

  public IslandWorldGenerator(
    int mapSize,
    double coastFraction,
    int oceanMargin,
    int seaLevel,
    List<String> biomeKeys,
    double climateSweep,
    double climateScale,
    double climateWarp,
    int climateOctaves
  ) {
    this.mask = new IslandMask(mapSize, coastFraction, oceanMargin);
    this.provider = new ClimateBiomeProvider(
      mask,
      biomeKeys,
      climateSweep,
      climateScale,
      climateWarp,
      climateOctaves
    );
    this.seaLevel = seaLevel;
    this.seaFloor = seaLevel - 8;
  }

  public IslandMask mask() {
    return mask;
  }

  public ClimateBiomeProvider provider() {
    return provider;
  }

  @Override
  public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
    return provider;
  }

  // True delegates the stage to vanilla before our (empty) hook runs, which is
  // exactly what we want for terrain and caves.
  @Override
  public boolean shouldGenerateNoise() {
    return true;
  }

  @Override
  public boolean shouldGenerateSurface() {
    return true;
  }

  @Override
  public boolean shouldGenerateCaves() {
    return true;
  }

  @Override
  public boolean shouldGenerateDecorations() {
    return false;
  }

  @Override
  public boolean shouldGenerateStructures() {
    return false;
  }

  @Override
  public boolean shouldGenerateMobs() {
    return false;
  }

  @Override
  public void generateSurface(
    WorldInfo info,
    Random random,
    int chunkX,
    int chunkZ,
    ChunkData data
  ) {
    mask.apply(data, chunkX, chunkZ, seaFloor, seaLevel);
  }
}
