package dev.bisz.world.wilderness;

import dev.bisz.world.config.WorldSettings;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Places ore veins using the vanilla {@code minecraft:ore} values (vein size,
 * veins per chunk, height distribution, shape, and air-exposure discard) with
 * two tweaks: slightly more ore overall, and a mild bias against fully buried
 * veins.
 */
public final class OreReseeder {

  private enum BiomeFilter {
    ANY,
    MOUNTAIN,
    BADLANDS,
  }

  /**
   * One vanilla-style ore placement.
   *
   * @param size vein size
   * @param countMin minimum veins per chunk
   * @param countMax maximum veins per chunk (equal to min for a fixed count)
   * @param heightMin minimum placement Y
   * @param heightMax maximum placement Y
   * @param trapezoid true for a triangular height distribution, false uniform
   * @param discard chance to discard an ore block exposed to air
   * @param rarity one vein every {@code rarity} chunks (1 = always)
   */
  private record OreVein(
    Material ore,
    Material deepslate,
    int size,
    int countMin,
    int countMax,
    int heightMin,
    int heightMax,
    boolean trapezoid,
    double discard,
    int rarity,
    boolean nether,
    BiomeFilter biome
  ) {}

  private static final List<OreVein> VEINS = List.of(
    // Coal
    vein(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, 17, 30, 136, 320, false, 0.0),
    vein(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, 17, 20, 0, 192, true, 0.5),
    // Iron
    vein(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 9, 90, 80, 320, true, 0.0),
    vein(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 9, 10, -24, 56, true, 0.0),
    vein(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 4, 10, -64, 72, false, 0.0),
    // Copper
    vein(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, 10, 16, -16, 112, true, 0.0),
    vein(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, 20, 16, -16, 112, true, 0.0),
    // Gold
    vein(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, 9, 4, -64, 32, true, 0.5),
    vein(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, 9, 0, 1, -64, -48, false, 0.5, 1, false, BiomeFilter.ANY),
    vein(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, 9, 50, 50, 32, 256, false, 0.0, 1, false, BiomeFilter.BADLANDS),
    // Redstone
    vein(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, 8, 4, -64, 15, false, 0.0),
    vein(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, 8, 8, -64, -32, true, 0.0),
    // Lapis
    vein(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, 7, 2, -32, 32, true, 0.0),
    vein(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, 7, 4, -64, 64, false, 1.0),
    // Diamond
    vein(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 4, 7, -64, 16, true, 0.5),
    vein(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 8, 2, -64, -4, false, 0.5),
    vein(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 12, 1, 1, -64, 16, true, 0.7, 9, false, BiomeFilter.ANY),
    vein(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 8, 4, -64, 16, true, 1.0),
    // Emerald (mountains only)
    vein(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, 3, 100, 100, -16, 320, true, 0.0, 1, false, BiomeFilter.MOUNTAIN),
    // Nether
    vein(Material.NETHER_QUARTZ_ORE, Material.NETHER_QUARTZ_ORE, 14, 16, 16, 10, 117, false, 0.0, 1, true, BiomeFilter.ANY),
    vein(Material.NETHER_GOLD_ORE, Material.NETHER_GOLD_ORE, 10, 10, 10, 10, 117, false, 0.0, 1, true, BiomeFilter.ANY),
    vein(Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS, 3, 1, 1, 8, 24, true, 1.0, 1, true, BiomeFilter.ANY),
    vein(Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS, 2, 1, 1, 8, 24, true, 1.0, 1, true, BiomeFilter.ANY)
  );

  /**
   * Fraction of a vein's nominal size that survives the capsule shape and the
   * exposure discards; calibrated against live chunks so the legacy baseline
   * estimate is in the right ballpark.
   */
  private static final double VEIN_FILL_FACTOR = 0.3;

  private final WorldSettings settings;

  public OreReseeder(WorldSettings settings) {
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /**
   * Places this chunk's ore veins.
   *
   * @return the number of ore blocks placed, used as the resource baseline
   */
  public int reseed(World world, int chunkX, int chunkZ, Random random) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(random, "random");
    boolean nether = world.getEnvironment() == World.Environment.NETHER;
    int minX = chunkX << 4;
    int minZ = chunkZ << 4;
    int worldMin = world.getMinHeight();
    int worldMax = world.getMaxHeight() - 1;
    int placed = 0;
    for (OreVein vein : VEINS) {
      if (vein.nether() != nether) continue;
      if (vein.rarity() > 1 && random.nextInt(vein.rarity()) != 0) continue;
      if (
        vein.biome() != BiomeFilter.ANY &&
        !matchesBiome(world, minX + 8, minZ + 8, vein.biome())
      ) {
        continue;
      }
      int count = vein.countMin();
      if (vein.countMax() > vein.countMin()) {
        count += random.nextInt(vein.countMax() - vein.countMin() + 1);
      }
      count = (int) Math.round(count * settings.oreCountMultiplier());
      for (int index = 0; index < count; index++) {
        int x = minX + random.nextInt(16);
        int z = minZ + random.nextInt(16);
        int y = sampleHeight(random, vein, worldMin, worldMax);
        if (
          settings.oreExposureWeight() > 0.0 &&
          !isExposed(world, x, y, z, 1) &&
          random.nextDouble() < settings.oreExposureWeight()
        ) {
          continue;
        }
        placed += placeVein(world, random, x, y, z, vein);
      }
    }
    return placed;
  }

  /**
   * Rough expected ore blocks per chunk for a freshly generated world. Used to
   * seed the resource baseline of chunks generated before this accounting was
   * added, so depletion still triggers.
   */
  public int estimateNodesPerChunk(boolean nether) {
    int total = 0;
    for (OreVein vein : VEINS) {
      if (vein.nether() != nether) continue;
      if (vein.rarity() > 1) continue;
      double expectedCount = (vein.countMin() + vein.countMax()) / 2.0;
      total += (int) Math.round(expectedCount * vein.size() * VEIN_FILL_FACTOR);
    }
    return Math.max(1, (int) Math.round(total * settings.oreCountMultiplier()));
  }

  private static int sampleHeight(
    Random random,
    OreVein vein,
    int worldMin,
    int worldMax
  ) {
    int low = Math.max(vein.heightMin(), worldMin + 1);
    int high = Math.min(vein.heightMax(), worldMax - 1);
    if (high <= low) return low;
    double t = vein.trapezoid()
      ? (random.nextDouble() + random.nextDouble()) / 2.0
      : random.nextDouble();
    return low + (int) Math.round(t * (high - low));
  }

  /**
   * Places a vein using the vanilla {@code minecraft:ore} shape: a slim capsule
   * around a random line segment, with a sine profile that is thickest in the
   * middle.
   */
  private int placeVein(
    World world,
    Random random,
    int x,
    int y,
    int z,
    OreVein vein
  ) {
    int placed = 0;
    float angle = random.nextFloat() * (float) Math.PI;
    float halfSize = vein.size() / 8.0F;
    double x0 = x + Math.sin(angle) * halfSize;
    double x1 = x - Math.sin(angle) * halfSize;
    double z0 = z + Math.cos(angle) * halfSize;
    double z1 = z - Math.cos(angle) * halfSize;
    double y0 = y + random.nextInt(3) - 2;
    double y1 = y + random.nextInt(3) - 2;
    int points = Math.max(1, vein.size());
    for (int k = 0; k < points; k++) {
      float f = (float) k / (float) points;
      double px = lerp(f, x0, x1);
      double py = lerp(f, y0, y1);
      double pz = lerp(f, z0, z1);
      double h = random.nextDouble() * points / 16.0;
      double radius = ((Math.sin(Math.PI * f) + 1.0) * h + 1.0) / 2.0;
      int r = (int) Math.ceil(radius);
      int cx = (int) Math.floor(px);
      int cy = (int) Math.floor(py);
      int cz = (int) Math.floor(pz);
      for (int bx = cx - r; bx <= cx + r; bx++) {
        for (int by = cy - r; by <= cy + r; by++) {
          for (int bz = cz - r; bz <= cz + r; bz++) {
            double dx = bx + 0.5 - px;
            double dy = by + 0.5 - py;
            double dz = bz + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
              if (placeBlock(world, random, bx, by, bz, vein)) placed++;
            }
          }
        }
      }
    }
    return placed;
  }

  private static double lerp(double t, double a, double b) {
    return a + t * (b - a);
  }

  private static boolean placeBlock(
    World world,
    Random random,
    int x,
    int y,
    int z,
    OreVein vein
  ) {
    Block block = world.getBlockAt(x, y, z);
    Material current = block.getType();
    Material replacement;
    if (
      current == Material.STONE ||
      current == Material.GRANITE ||
      current == Material.DIORITE ||
      current == Material.ANDESITE ||
      current == Material.TUFF
    ) {
      replacement = vein.ore();
    } else if (current == Material.DEEPSLATE) {
      replacement = vein.deepslate();
    } else if (vein.nether() && current == Material.NETHERRACK) {
      replacement = vein.ore();
    } else {
      return false;
    }
    if (
      vein.discard() > 0.0 &&
      isAdjacentToAir(world, x, y, z) &&
      random.nextDouble() < vein.discard()
    ) {
      return false;
    }
    block.setType(replacement, false);
    return true;
  }

  /** Whether any of the six face neighbours is air. */
  private static boolean isAdjacentToAir(World world, int x, int y, int z) {
    return (
      world.getBlockAt(x + 1, y, z).getType().isAir() ||
      world.getBlockAt(x - 1, y, z).getType().isAir() ||
      world.getBlockAt(x, y + 1, z).getType().isAir() ||
      world.getBlockAt(x, y - 1, z).getType().isAir() ||
      world.getBlockAt(x, y, z + 1).getType().isAir() ||
      world.getBlockAt(x, y, z - 1).getType().isAir()
    );
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

  private static boolean matchesBiome(
    World world,
    int x,
    int z,
    BiomeFilter filter
  ) {
    int y = world.getHighestBlockYAt(x, z);
    String key = world.getBiome(x, y, z).getKey().getKey();
    return switch (filter) {
      case MOUNTAIN ->
        key.contains("windswept") ||
        key.contains("mountain") ||
        key.contains("stony_peaks") ||
        key.contains("jagged") ||
        key.contains("frozen_peaks") ||
        key.contains("snowy_slopes") ||
        key.contains("meadow") ||
        key.contains("grove") ||
        key.contains("cherry_grove");
      case BADLANDS -> key.contains("badlands");
      case ANY -> true;
    };
  }

  private static OreVein vein(
    Material ore,
    Material deepslate,
    int size,
    int count,
    int heightMin,
    int heightMax,
    boolean trapezoid,
    double discard
  ) {
    return vein(
      ore,
      deepslate,
      size,
      count,
      count,
      heightMin,
      heightMax,
      trapezoid,
      discard,
      1,
      false,
      BiomeFilter.ANY
    );
  }

  private static OreVein vein(
    Material ore,
    Material deepslate,
    int size,
    int countMin,
    int countMax,
    int heightMin,
    int heightMax,
    boolean trapezoid,
    double discard,
    int rarity,
    boolean nether,
    BiomeFilter biome
  ) {
    return new OreVein(
      ore,
      deepslate,
      size,
      countMin,
      countMax,
      heightMin,
      heightMax,
      trapezoid,
      discard,
      rarity,
      nether,
      biome
    );
  }
}
