package dev.bisz.worldgen.sim;

import dev.bisz.worldgen.snapshot.ChunkSnapshot;
import dev.bisz.worldgen.wilderness.ChunkRegenerator;
import dev.bisz.worldgen.wilderness.LootReseeder;
import dev.bisz.worldgen.wilderness.OreReseeder;
import dev.bisz.worldgen.wilderness.SmallStructures;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;

/**
 * Runs the real wilderness regeneration pipeline on a stored or synthetic
 * chunk snapshot inside the admin dimension, so the algorithm can be watched
 * and measured without touching live terrain.
 */
public final class RegenerationSimulator {

  /** The outcome of one simulation run. */
  public record SimulationReport(
    boolean ok,
    int cycles,
    int stagingChunkX,
    int stagingChunkZ,
    int oreBlocksBefore,
    int oreBlocksAfter,
    int containersBefore,
    int containersAfter,
    long elapsedMillis,
    List<String> notes
  ) {}

  private final AdminDimensionService admin;
  private final ChunkRegenerator regenerator;
  private final OreReseeder ores;
  private final SmallStructures smallStructures;
  private final LootReseeder loot;

  public RegenerationSimulator(
    AdminDimensionService admin,
    ChunkRegenerator regenerator,
    OreReseeder ores,
    SmallStructures smallStructures,
    LootReseeder loot
  ) {
    this.admin = Objects.requireNonNull(admin, "admin");
    this.regenerator = Objects.requireNonNull(regenerator, "regenerator");
    this.ores = Objects.requireNonNull(ores, "ores");
    this.smallStructures = Objects.requireNonNull(smallStructures, "smallStructures");
    this.loot = Objects.requireNonNull(loot, "loot");
  }

  /**
   * Stages the snapshot and runs the regeneration pipeline the supplied number
   * of times, reporting the block-level effect.
   */
  public SimulationReport simulate(ChunkSnapshot snapshot, int cycles, long seed) {
    Objects.requireNonNull(snapshot, "snapshot");
    List<String> notes = new ArrayList<>();
    World world = admin.world();
    if (world == null) {
      return new SimulationReport(
        false,
        0,
        AdminDimensionService.STAGING_CHUNK_X,
        AdminDimensionService.STAGING_CHUNK_Z,
        0,
        0,
        0,
        0,
        0L,
        List.of("Admin dimension is unavailable.")
      );
    }
    long started = System.currentTimeMillis();
    int chunkX = AdminDimensionService.STAGING_CHUNK_X;
    int chunkZ = AdminDimensionService.STAGING_CHUNK_Z;
    admin.clearChunk(chunkX, chunkZ);
    admin.restore(snapshot, chunkX, chunkZ);
    int oresBefore = countOres(world, chunkX, chunkZ, snapshot);
    int containersBefore = countContainers(world, chunkX, chunkZ, snapshot);
    notes.add("Snapshot from " + snapshot.world() + " staged in admin dimension.");
    if (regenerator instanceof dev.bisz.worldgen.wilderness.NoopChunkRegenerator) {
      notes.add("No regeneration backend; ore stripping was skipped.");
    }
    for (int cycle = 1; cycle <= Math.max(1, cycles); cycle++) {
      Random random = new Random(seed + cycle * 7919L);
      regenerator.stripOres(world, chunkX, chunkZ);
      ores.reseed(world, chunkX, chunkZ, random);
      smallStructures.place(world, chunkX, chunkZ, random);
      loot.reseed(world, chunkX, chunkZ, random);
      notes.add("Cycle " + cycle + " applied.");
    }
    int oresAfter = countOres(world, chunkX, chunkZ, snapshot);
    int containersAfter = countContainers(world, chunkX, chunkZ, snapshot);
    return new SimulationReport(
      true,
      Math.max(1, cycles),
      chunkX,
      chunkZ,
      oresBefore,
      oresAfter,
      containersBefore,
      containersAfter,
      System.currentTimeMillis() - started,
      List.copyOf(notes)
    );
  }

  private int countOres(
    World world,
    int chunkX,
    int chunkZ,
    ChunkSnapshot snapshot
  ) {
    return count(world, chunkX, chunkZ, snapshot, block -> isOre(block.getType()));
  }

  private int countContainers(
    World world,
    int chunkX,
    int chunkZ,
    ChunkSnapshot snapshot
  ) {
    return count(
      world,
      chunkX,
      chunkZ,
      snapshot,
      block -> block.getState() instanceof Container
    );
  }

  private int count(
    World world,
    int chunkX,
    int chunkZ,
    ChunkSnapshot snapshot,
    java.util.function.Predicate<Block> predicate
  ) {
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    int low = Math.max(snapshot.minY(), world.getMinHeight());
    int high = Math.min(snapshot.maxY(), world.getMaxHeight() - 1);
    int total = 0;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = low; y <= high; y++) {
          if (predicate.test(world.getBlockAt(baseX + x, y, baseZ + z))) {
            total++;
          }
        }
      }
    }
    return total;
  }

  private static boolean isOre(Material material) {
    if (material == Material.ANCIENT_DEBRIS) return true;
    return material.name().endsWith("_ORE");
  }
}
