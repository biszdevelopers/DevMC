package dev.bisz.worldgen.feature;

import dev.bisz.worldgen.config.WorldSettings;
import dev.bisz.worldgen.structure.PoiService;
import dev.bisz.worldgen.wilderness.LootReseeder;
import dev.bisz.worldgen.wilderness.OreReseeder;
import java.util.Objects;
import java.util.Random;
import org.bukkit.World;

/**
 * Applies the non-structure features that sit on top of the barebone terrain:
 * ores, plants, animals, and small POIs. Used both when a world is first
 * generated and when a chunk is regenerated.
 */
public final class FeatureGenerator {

  private final WorldSettings settings;
  private final OreReseeder ores;
  private final PoiService pois;
  private final LootReseeder loot;
  private final PlantGenerator plants = new PlantGenerator();
  private final AnimalGenerator animals = new AnimalGenerator();

  public FeatureGenerator(
    WorldSettings settings,
    OreReseeder ores,
    PoiService pois,
    LootReseeder loot
  ) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.ores = Objects.requireNonNull(ores, "ores");
    this.pois = Objects.requireNonNull(pois, "pois");
    this.loot = Objects.requireNonNull(loot, "loot");
  }

  /**
   * Applies every enabled feature to a chunk.
   *
   * @return the number of resource nodes (ore blocks, POIs) placed, used as
   *     the chunk's resource baseline
   */
  public int apply(World world, int chunkX, int chunkZ, Random random) {
    int nodes = ores.reseed(world, chunkX, chunkZ, random);
    nodes += pois.reseed(world, chunkX, chunkZ, random);
    if (settings.plantsEnabled()) {
      plants.reseed(world, chunkX, chunkZ, random);
    }
    if (settings.animalsEnabled()) {
      animals.reseed(
        world,
        chunkX,
        chunkZ,
        random,
        settings.animalsPerChunk()
      );
    }
    return nodes;
  }

  /**
   * A rough baseline for chunks generated before resource accounting existed,
   * so their depletion still triggers regeneration.
   */
  public int estimateBaseline(World world) {
    boolean nether = world.getEnvironment() == World.Environment.NETHER;
    int nodes = ores.estimateNodesPerChunk(nether);
    if (settings.structuresEnabled()) nodes += 1;
    return Math.max(1, nodes);
  }
}
