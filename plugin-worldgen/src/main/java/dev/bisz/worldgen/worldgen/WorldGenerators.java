package dev.bisz.worldgen.worldgen;

import dev.bisz.worldgen.config.WorldSettings;
import java.util.Objects;
import org.bukkit.generator.ChunkGenerator;

/**
 * The single place the managed world's generator is built. The live world, the
 * scratch regeneration world, and the on-disk reload path all use this so their
 * terrain and biomes match exactly.
 */
public final class WorldGenerators {

  private WorldGenerators() {}

  public static ChunkGenerator create(WorldSettings settings) {
    Objects.requireNonNull(settings, "settings");
    if (settings.islandEnabled()) {
      return new IslandWorldGenerator(
        settings.islandSize(),
        settings.islandCoastFraction(),
        settings.islandOceanMargin(),
        settings.islandSeaLevel(),
        settings.worldBiomes(),
        settings.climateSweep(),
        settings.climateScale(),
        settings.climateWarp(),
        settings.climateOctaves()
      );
    }
    return new BareboneGenerator(
      settings.terrainAmplitude(),
      settings.terrainCaves(),
      settings.terrainCaveScale(),
      settings.terrainCaveThreshold(),
      settings.islandSeaLevel()
    );
  }
}
