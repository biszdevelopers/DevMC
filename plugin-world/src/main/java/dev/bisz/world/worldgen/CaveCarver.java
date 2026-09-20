package dev.bisz.world.worldgen;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * Deterministic value-noise cave carver shared by the barebone and Rust-map
 * generators. It only removes soft rock, never bedrock or surface soil.
 */
public final class CaveCarver {

  private CaveCarver() {}

  /** Carves caves into a chunk between the bedrock floor and just below the surface. */
  public static void carve(
    ChunkData data,
    long seed,
    int chunkX,
    int chunkZ,
    double scale,
    double threshold
  ) {
    double frequency = Math.max(0.001, scale);
    int minY = data.getMinHeight();
    int maxY = data.getMaxHeight();
    int bottom = minY + 6;
    for (int x = 0; x < 16; x++) {
      int worldX = (chunkX << 4) + x;
      for (int z = 0; z < 16; z++) {
        int worldZ = (chunkZ << 4) + z;
        int surface = data.getHeight(HeightMap.MOTION_BLOCKING, x, z);
        int top = Math.min(surface - 4, maxY - 2);
        for (int y = bottom; y < top; y++) {
          if (!isCarveable(data.getType(x, y, z))) continue;
          double value = noise(
            seed,
            worldX * frequency,
            y * frequency * 1.6,
            worldZ * frequency
          );
          if (value > threshold) data.setBlock(x, y, z, Material.AIR);
        }
      }
    }
  }

  private static boolean isCarveable(Material material) {
    return (
      material == Material.STONE ||
      material == Material.DEEPSLATE ||
      material == Material.GRANITE ||
      material == Material.DIORITE ||
      material == Material.ANDESITE ||
      material == Material.TUFF ||
      material == Material.DIRT ||
      material == Material.GRAVEL
    );
  }

  private static double noise(long seed, double x, double y, double z) {
    double value = octave(seed, x, y, z);
    value += 0.5 * octave(seed ^ 0x9E3779B97F4A7C15L, x * 2, y * 2, z * 2);
    return value / 1.5;
  }

  private static double octave(long seed, double x, double y, double z) {
    int xi = (int) Math.floor(x);
    int yi = (int) Math.floor(y);
    int zi = (int) Math.floor(z);
    double xf = x - xi;
    double yf = y - yi;
    double zf = z - zi;
    double u = xf * xf * (3 - 2 * xf);
    double v = yf * yf * (3 - 2 * yf);
    double w = zf * zf * (3 - 2 * zf);
    double c000 = hash(seed, xi, yi, zi);
    double c100 = hash(seed, xi + 1, yi, zi);
    double c010 = hash(seed, xi, yi + 1, zi);
    double c110 = hash(seed, xi + 1, yi + 1, zi);
    double c001 = hash(seed, xi, yi, zi + 1);
    double c101 = hash(seed, xi + 1, yi, zi + 1);
    double c011 = hash(seed, xi, yi + 1, zi + 1);
    double c111 = hash(seed, xi + 1, yi + 1, zi + 1);
    double x00 = c000 + u * (c100 - c000);
    double x10 = c010 + u * (c110 - c010);
    double x01 = c001 + u * (c101 - c001);
    double x11 = c011 + u * (c111 - c011);
    double y0 = x00 + v * (x10 - x00);
    double y1 = x01 + v * (x11 - x01);
    return y0 + w * (y1 - y0);
  }

  private static double hash(long seed, int x, int y, int z) {
    long h = seed;
    h ^= x * 0x9E3779B97F4A7C15L;
    h ^= y * 0xC2B2AE3D27D4EB4FL;
    h ^= z * 0x165667B19E3779F9L;
    h ^= h >>> 29;
    h *= 0xBF58476D1CE4E5B9L;
    h ^= h >>> 32;
    return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
  }
}
