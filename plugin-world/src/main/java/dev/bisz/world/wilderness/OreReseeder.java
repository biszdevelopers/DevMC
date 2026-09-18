package dev.bisz.world.wilderness;

import dev.bisz.world.config.WorldSettings;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Places randomized ore veins after a chunk's terrain has been regenerated and
 * its natural ores stripped. Veins are rough blobs sized per ore type, so the
 * distribution changes every cycle.
 */
public final class OreReseeder {

  private record OreType(
    Material material,
    Material deepslate,
    int minY,
    int maxY,
    int weight,
    int veinMin,
    int veinMax,
    boolean nether
  ) {}

  private static final OreType[] ORES = {
    new OreType(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, 0, 160, 30, 8, 17, false),
    new OreType(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, -16, 112, 22, 6, 14, false),
    new OreType(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, -60, 72, 24, 4, 10, false),
    new OreType(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, -60, 32, 10, 3, 8, false),
    new OreType(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, -60, 16, 10, 4, 9, false),
    new OreType(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, -60, 32, 6, 3, 7, false),
    new OreType(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, -60, 16, 4, 2, 6, false),
    new OreType(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, -16, 320, 2, 1, 3, false),
    new OreType(Material.NETHER_QUARTZ_ORE, Material.NETHER_QUARTZ_ORE, 0, 120, 18, 4, 12, true),
    new OreType(Material.NETHER_GOLD_ORE, Material.NETHER_GOLD_ORE, 0, 120, 14, 3, 10, true),
    new OreType(Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS, 8, 119, 3, 1, 3, true),
  };

  private final WorldSettings settings;

  public OreReseeder(WorldSettings settings) {
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** Places this chunk's ore veins. */
  public void reseed(World world, int chunkX, int chunkZ, Random random) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(random, "random");
    int minX = chunkX << 4;
    int minZ = chunkZ << 4;
    int worldMin = world.getMinHeight();
    int worldMax = world.getMaxHeight() - 1;
    int veins = settings.oreVeinsPerChunk();
    for (int vein = 0; vein < veins; vein++) {
      OreType type = pick(random);
      int x = minX + random.nextInt(16);
      int z = minZ + random.nextInt(16);
      int y = randomBetween(
        random,
        type.minY(),
        type.maxY(),
        worldMin,
        worldMax
      );
      if (
        settings.oreExposureWeight() > 0.0 &&
        !isExposed(world, x, y, z, 2) &&
        random.nextDouble() < settings.oreExposureWeight()
      ) {
        continue;
      }
      placeVein(world, random, x, y, z, type);
    }
  }

  /** Whether any block within a small cube is air, i.e. cave-exposed. */
  private static boolean isExposed(
    World world,
    int x,
    int y,
    int z,
    int radius
  ) {
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dz = -radius; dz <= radius; dz++) {
          if (dx == 0 && dy == 0 && dz == 0) continue;
          if (world.getBlockAt(x + dx, y + dy, z + dz).getType().isAir()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private OreType pick(Random random) {
    int total = 0;
    for (OreType ore : ORES) total += ore.weight();
    int target = random.nextInt(total);
    int cursor = 0;
    for (OreType ore : ORES) {
      cursor += ore.weight();
      if (target < cursor) return ore;
    }
    return ORES[0];
  }

  private void placeVein(
    World world,
    Random random,
    int x,
    int y,
    int z,
    OreType type
  ) {
    int size =
      type.veinMin() + random.nextInt(type.veinMax() - type.veinMin() + 1);
    double radius = Math.max(1.0, Math.cbrt(size));
    for (int index = 0; index < size; index++) {
      int bx = x + offset(random, radius);
      int by = y + offset(random, radius);
      int bz = z + offset(random, radius);
      Block block = world.getBlockAt(bx, by, bz);
      Material current = block.getType();
      if (current == Material.STONE) {
        block.setType(type.material(), false);
      } else if (current == Material.DEEPSLATE) {
        block.setType(type.deepslate(), false);
      } else if (type.nether() && current == Material.NETHERRACK) {
        block.setType(type.material(), false);
      }
    }
  }

  private static int offset(Random random, double radius) {
    return (int) Math.round((random.nextDouble() * 2.0 - 1.0) * radius);
  }

  private static int randomBetween(
    Random random,
    int min,
    int max,
    int worldMin,
    int worldMax
  ) {
    int low = Math.max(min, worldMin + 1);
    int high = Math.min(max, worldMax - 1);
    if (high <= low) return low;
    return low + random.nextInt(high - low + 1);
  }
}
