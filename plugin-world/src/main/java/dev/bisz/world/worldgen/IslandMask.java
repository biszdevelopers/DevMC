package dev.bisz.world.worldgen;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * Shapes vanilla terrain into a bounded island by vertically compressing the
 * outer coastal band toward the sea floor and flooding it to sea level. The
 * inner core is left completely untouched, so its vanilla shape and caves are
 * preserved. The falloff is a pure function of the world coordinates, so it is
 * identical in the live and scratch worlds.
 */
public final class IslandMask {

  private final int islandRadius;
  private final int landRadius;

  public IslandMask(int mapSize, double coastFraction, int oceanMargin) {
    int half = Math.max(128, mapSize) / 2;
    this.islandRadius = Math.max(64, half - Math.max(0, oceanMargin));
    double coast = clamp(coastFraction, 0.0, 0.9);
    this.landRadius = Math.max(
      32,
      (int) Math.round(islandRadius * (1.0 - coast))
    );
  }

  public int islandRadius() {
    return islandRadius;
  }

  public int landRadius() {
    return landRadius;
  }

  /** 1 in the vanilla core, smoothly falling to 0 at the island edge. */
  public double falloff(double x, double z) {
    double distance = Math.hypot(x, z);
    if (distance <= landRadius) return 1.0;
    if (distance >= islandRadius) return 0.0;
    double t = (distance - landRadius) / (double) (islandRadius - landRadius);
    return smoothstep(1.0 - t);
  }

  /** True where the mask has carved ocean, so the biome should be ocean. */
  public boolean carvedOcean(double x, double z, double cut) {
    return falloff(x, z) <= cut;
  }

  /**
   * Compresses this chunk's above-sea columns in the coastal band. Columns in
   * the core, and columns already below sea level, are skipped.
   */
  public void apply(
    ChunkData data,
    int chunkX,
    int chunkZ,
    int seaFloor,
    int seaLevel
  ) {
    int minY = data.getMinHeight();
    int maxY = data.getMaxHeight();
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    for (int x = 0; x < 16; x++) {
      int worldX = baseX + x;
      for (int z = 0; z < 16; z++) {
        int worldZ = baseZ + z;
        double falloff = falloff(worldX, worldZ);
        if (falloff >= 1.0) continue;

        int surface = minY;
        for (int y = maxY - 1; y > seaFloor; y--) {
          if (!data.getType(x, y, z).isAir()) {
            surface = y;
            break;
          }
        }
        if (surface <= seaFloor) continue;

        int height = surface - seaFloor;
        Material[] column = new Material[height + 1];
        for (int y = 0; y <= height; y++) {
          column[y] = data.getType(x, seaFloor + y, z);
        }
        for (int y = seaFloor + 1; y < maxY; y++) {
          data.setBlock(x, y, z, Material.AIR);
        }
        for (int y = 1; y <= height; y++) {
          Material material = column[y];
          if (material.isAir()) continue;
          int target = seaFloor + (int) Math.round(y * falloff);
          if (target > seaFloor + height) target = seaFloor + height;
          data.setBlock(x, target, z, material);
        }

        int newSurface = seaFloor;
        for (int y = maxY - 1; y > seaFloor; y--) {
          if (!data.getType(x, y, z).isAir()) {
            newSurface = y;
            break;
          }
        }
        if (newSurface <= seaLevel) {
          // Carved coast: give the new seabed a sandy floor instead of the
          // land surface that was compressed down here.
          data.setBlock(x, newSurface, z, Material.SAND);
        }
        for (int y = newSurface + 1; y <= seaLevel && y < maxY; y++) {
          if (data.getType(x, y, z).isAir()) {
            data.setBlock(x, y, z, Material.WATER);
          }
        }
      }
    }
  }

  private static double smoothstep(double t) {
    double value = clamp(t, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
  }

  private static double clamp(double value, double min, double max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }
}
