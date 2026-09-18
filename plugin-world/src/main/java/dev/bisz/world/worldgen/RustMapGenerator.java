package dev.bisz.world.worldgen;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Generates a large island in the style of a Rust map: a smooth radial falloff
 * to ocean, one biome per angular sector, and terrain only. Ores, decorations,
 * and structures are intentionally disabled so the settlement plugin's own ore
 * reseeder and structure system place them.
 */
public final class RustMapGenerator extends ChunkGenerator {

  private final int islandRadius;
  private final int seaLevel;
  private final List<String> biomeKeys;

  public RustMapGenerator(
    int islandRadius,
    int seaLevel,
    List<String> biomeKeys
  ) {
    this.islandRadius = Math.max(64, islandRadius);
    this.seaLevel = seaLevel;
    this.biomeKeys = List.copyOf(Objects.requireNonNull(biomeKeys, "biomeKeys"));
  }

  @Override
  public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
    return new RustMapBiomeProvider(islandRadius, biomeKeys);
  }

  @Override
  public boolean shouldGenerateNoise() {
    return true;
  }

  @Override
  public boolean shouldGenerateSurface() {
    return true;
  }

  @Override
  public boolean shouldGenerateBedrock() {
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
  public void generateBedrock(
    WorldInfo info,
    Random random,
    int chunkX,
    int chunkZ,
    ChunkData data
  ) {
    int minY = info.getMinHeight();
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        data.setBlock(x, minY, z, Material.BEDROCK);
        if (random.nextFloat() < 0.4F && minY + 1 < info.getMaxHeight()) {
          data.setBlock(x, minY + 1, z, Material.BEDROCK);
        }
      }
    }
  }

  @Override
  public void generateNoise(
    WorldInfo info,
    Random random,
    int chunkX,
    int chunkZ,
    ChunkData data
  ) {
    int minY = info.getMinHeight();
    int maxY = info.getMaxHeight();
    long seed = info.getSeed();
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        int worldX = (chunkX << 4) + x;
        int worldZ = (chunkZ << 4) + z;
        int height = height(worldX, worldZ, seed);
        for (int y = minY; y < maxY; y++) {
          Material material;
          if (y > height) {
            material = y <= seaLevel ? Material.WATER : Material.AIR;
          } else if (y < 0) {
            material = Material.DEEPSLATE;
          } else {
            material = Material.STONE;
          }
          if (material != Material.AIR) data.setBlock(x, y, z, material);
        }
      }
    }
  }

  @Override
  public void generateSurface(
    WorldInfo info,
    Random random,
    int chunkX,
    int chunkZ,
    ChunkData data
  ) {
    int minY = info.getMinHeight();
    long seed = info.getSeed();
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        int worldX = (chunkX << 4) + x;
        int worldZ = (chunkZ << 4) + z;
        int height = height(worldX, worldZ, seed);
        if (height <= seaLevel) {
          for (int y = height; y > height - 3 && y >= minY; y--) {
            data.setBlock(x, y, z, Material.SAND);
          }
        } else {
          data.setBlock(x, height, z, Material.GRASS_BLOCK);
          for (int y = height - 1; y >= height - 3 && y >= minY; y--) {
            data.setBlock(x, y, z, Material.DIRT);
          }
        }
      }
    }
  }

  /** The terrain height at a world column. */
  public int height(int worldX, int worldZ, long seed) {
    double distance = Math.hypot(worldX, worldZ);
    double falloff = 1.0 - Math.min(1.0, distance / islandRadius);
    falloff = falloff * falloff * (3.0 - 2.0 * falloff);
    double noise =
      Math.sin(worldX * 0.012 + seed * 0.0001) * Math.cos(worldZ * 0.012) +
      0.5 * Math.sin(worldX * 0.03) * Math.cos(worldZ * 0.03);
    double amplitude = 16.0 * falloff;
    return (int) Math.round(seaLevel - 8 + falloff * 12 + noise * amplitude);
  }

  public int islandRadius() {
    return islandRadius;
  }

  public int seaLevel() {
    return seaLevel;
  }
}
