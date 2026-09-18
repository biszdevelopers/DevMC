package dev.bisz.world.feature;

import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.wilderness.LootReseeder;
import dev.bisz.world.wilderness.OreReseeder;
import dev.bisz.world.wilderness.SmallStructures;
import java.util.Objects;
import java.util.Random;
import org.bukkit.World;

/**
 * Applies the non-structure features that sit on top of the barebone terrain:
 * ores, plants, animals, loot caches, and small POIs. Used both when a world is
 * first generated and when a chunk is regenerated.
 */
public final class FeatureGenerator {

  private final WorldSettings settings;
  private final OreReseeder ores;
  private final SmallStructures smallStructures;
  private final LootReseeder loot;
  private final PlantGenerator plants = new PlantGenerator();
  private final AnimalGenerator animals = new AnimalGenerator();

  public FeatureGenerator(
    WorldSettings settings,
    OreReseeder ores,
    SmallStructures smallStructures,
    LootReseeder loot
  ) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.ores = Objects.requireNonNull(ores, "ores");
    this.smallStructures = Objects.requireNonNull(smallStructures, "smallStructures");
    this.loot = Objects.requireNonNull(loot, "loot");
  }

  /** Applies every enabled feature to a chunk. */
  public void apply(World world, int chunkX, int chunkZ, Random random) {
    ores.reseed(world, chunkX, chunkZ, random);
    smallStructures.reseed(world, chunkX, chunkZ, random);
    loot.reseed(world, chunkX, chunkZ, random);
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
  }
}
