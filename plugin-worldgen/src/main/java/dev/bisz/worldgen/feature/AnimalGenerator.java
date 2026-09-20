package dev.bisz.worldgen.feature;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

/**
 * Spawns a light scattering of passive animals on suitable surface terrain so
 * the wilderness supports hunting without becoming crowded.
 */
public final class AnimalGenerator {

  private static final EntityType[] PASSIVE = {
    EntityType.COW,
    EntityType.SHEEP,
    EntityType.PIG,
    EntityType.CHICKEN,
  };

  /** Spawns this chunk's animals, if any. */
  public void reseed(
    World world,
    int chunkX,
    int chunkZ,
    Random random,
    int perChunk
  ) {
    if (perChunk <= 0) return;
    int minX = chunkX << 4;
    int minZ = chunkZ << 4;
    for (int index = 0; index < perChunk; index++) {
      if (random.nextDouble() < 0.5) continue;
      int x = minX + random.nextInt(16);
      int z = minZ + random.nextInt(16);
      int y = world.getHighestBlockYAt(x, z);
      if (world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;
      if (!world.getBlockAt(x, y + 1, z).getType().isAir()) continue;
      EntityType type = PASSIVE[random.nextInt(PASSIVE.length)];
      world.spawnEntity(new Location(world, x + 0.5, y + 1, z + 0.5), type);
    }
  }
}
