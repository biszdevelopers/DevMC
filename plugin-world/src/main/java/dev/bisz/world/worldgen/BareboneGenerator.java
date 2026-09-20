package dev.bisz.world.worldgen;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Vanilla terrain with no features, plus two configurable tweaks:
 * a slight vertical amplitude increase above sea level and extra caves.
 *
 * <p>The server runs the vanilla noise, surface, and cave stages (so terrain
 * shape and biomes are vanilla), but decorations (ores, plants), structures,
 * and mobs are disabled. The plugin's feature pass places its own ores, plants,
 * animals, and POIs on top.
 */
public final class BareboneGenerator extends ChunkGenerator {

  private final double amplitude;
  private final boolean caves;
  private final double caveScale;
  private final double caveThreshold;
  private final int seaLevel;

  public BareboneGenerator() {
    this(1.0, false, 0.06, 0.62, 63);
  }

  public BareboneGenerator(
    double amplitude,
    boolean caves,
    double caveScale,
    double caveThreshold,
    int seaLevel
  ) {
    this.amplitude = Math.max(1.0, amplitude);
    this.caves = caves;
    this.caveScale = Math.max(0.001, caveScale);
    this.caveThreshold = caveThreshold;
    this.seaLevel = seaLevel;
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
  public void generateCaves(
    WorldInfo info,
    Random random,
    int chunkX,
    int chunkZ,
    ChunkData data
  ) {
    if (amplitude > 1.0) stretch(data);
    if (caves) {
      CaveCarver.carve(
        data,
        info.getSeed(),
        chunkX,
        chunkZ,
        caveScale,
        caveThreshold
      );
    }
  }

  /** Stretches each column vertically around sea level. */
  private void stretch(ChunkData data) {
    int minY = data.getMinHeight();
    int maxY = data.getMaxHeight();
    Material[] column = new Material[maxY - minY];
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = minY; y < maxY; y++) {
          column[y - minY] = data.getType(x, y, z);
        }
        for (int y = seaLevel + 1; y < maxY; y++) {
          int source = seaLevel + (int) Math.round((y - seaLevel) / amplitude);
          if (source < minY) source = minY;
          if (source >= maxY) source = maxY - 1;
          data.setBlock(x, y, z, column[source - minY]);
        }
      }
    }
  }

}
