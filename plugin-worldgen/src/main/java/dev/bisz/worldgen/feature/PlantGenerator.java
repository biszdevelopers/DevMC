package dev.bisz.worldgen.feature;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

/**
 * Generates biome-appropriate vegetation on top of the barebone terrain: trees
 * plus ground cover (grass and flowers). Kept deliberately light so it can run
 * as part of both initial generation and per-chunk regeneration.
 */
public final class PlantGenerator {

  private enum TreeKind {
    OAK(Material.OAK_LOG, Material.OAK_LEAVES),
    SPRUCE(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES),
    JUNGLE(Material.JUNGLE_LOG, Material.JUNGLE_LEAVES),
    ACACIA(Material.ACACIA_LOG, Material.ACACIA_LEAVES),
    NONE(Material.AIR, Material.AIR);

    private final Material log;
    private final Material leaves;

    TreeKind(Material log, Material leaves) {
      this.log = log;
      this.leaves = leaves;
    }
  }

  /** Places this chunk's plants. */
  public void reseed(World world, int chunkX, int chunkZ, Random random) {
    int minX = chunkX << 4;
    int minZ = chunkZ << 4;
    int trees = random.nextInt(3);
    for (int index = 0; index < trees; index++) {
      int x = minX + random.nextInt(16);
      int z = minZ + random.nextInt(16);
      int y = world.getHighestBlockYAt(x, z);
      Block ground = world.getBlockAt(x, y, z);
      if (!isSoil(ground.getType())) continue;
      if (!world.getBlockAt(x, y + 1, z).getType().isAir()) continue;
      TreeKind kind = treeKind(world.getBiome(x, y, z));
      if (kind == TreeKind.NONE) {
        if (random.nextDouble() < 0.5) {
          world.getBlockAt(x, y + 1, z).setType(Material.DEAD_BUSH, false);
        }
        continue;
      }
      placeTree(world, random, x, y + 1, z, kind);
    }
    for (int index = 0; index < 8; index++) {
      int x = minX + random.nextInt(16);
      int z = minZ + random.nextInt(16);
      int y = world.getHighestBlockYAt(x, z);
      if (world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;
      Block above = world.getBlockAt(x, y + 1, z);
      if (!above.getType().isAir()) continue;
      above.setType(
        random.nextDouble() < 0.8 ? Material.SHORT_GRASS : Material.DANDELION,
        false
      );
    }
  }

  private static void placeTree(
    World world,
    Random random,
    int x,
    int y,
    int z,
    TreeKind kind
  ) {
    int height = 4 + random.nextInt(3);
    for (int dy = 0; dy < height; dy++) {
      world.getBlockAt(x, y + dy, z).setType(kind.log, false);
    }
    int top = y + height;
    for (int dy = -2; dy <= 1; dy++) {
      int radius = dy <= -1 ? 2 : 1;
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dz = -radius; dz <= radius; dz++) {
          if (dx == 0 && dz == 0 && dy < 1) continue;
          if (
            Math.abs(dx) == radius &&
            Math.abs(dz) == radius &&
            random.nextBoolean()
          ) {
            continue;
          }
          Block block = world.getBlockAt(x + dx, top + dy, z + dz);
          if (block.getType().isAir()) {
            block.setType(kind.leaves, false);
          }
        }
      }
    }
  }

  private static boolean isSoil(Material material) {
    return (
      material == Material.GRASS_BLOCK ||
      material == Material.DIRT ||
      material == Material.PODZOL ||
      material == Material.COARSE_DIRT
    );
  }

  private static TreeKind treeKind(Biome biome) {
    String key = biome.getKey().getKey();
    if (key.contains("jungle")) return TreeKind.JUNGLE;
    if (key.contains("taiga") || key.contains("snowy")) return TreeKind.SPRUCE;
    if (key.contains("savanna")) return TreeKind.ACACIA;
    if (key.contains("desert") || key.contains("badlands")) return TreeKind.NONE;
    return TreeKind.OAK;
  }
}
