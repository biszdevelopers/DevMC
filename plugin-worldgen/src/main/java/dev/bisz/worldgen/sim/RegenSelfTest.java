package dev.bisz.worldgen.sim;

import dev.bisz.worldgen.WorldGenPlugin;
import dev.bisz.worldgen.model.ChunkKey;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * One-shot startup self-test for regeneration: places a marker block, forces a
 * chunk to regenerate, and reports whether the marker was cleared along with the
 * resulting ore count. Enabled by the {@code debug.verify_regen} setting.
 */
public final class RegenSelfTest {

  private final WorldGenPlugin plugin;

  public RegenSelfTest(WorldGenPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  /** Schedules the self-test to run shortly after startup. */
  public void schedule(int chunkX, int chunkZ) {
    Bukkit
      .getScheduler()
      .runTaskLater(plugin, () -> run(chunkX, chunkZ), 100L);
  }

  private void run(int chunkX, int chunkZ) {
    World world = plugin.setup().ensureWorld();
    if (world == null) {
      plugin.getLogger().warning("Regen self-test: no managed world.");
      return;
    }
    world.getChunkAt(chunkX, chunkZ);
    ChunkKey key = new ChunkKey(world.getName(), chunkX, chunkZ);
    int markerX = (chunkX << 4) + 8;
    int markerZ = (chunkZ << 4) + 8;
    int markerY = 120;
    Material before = world.getBlockAt(markerX, markerY, markerZ).getType();
    world.getBlockAt(markerX, markerY, markerZ).setType(Material.DIAMOND_BLOCK, false);
    boolean ok = plugin.regenerationScheduler().forceChunk(key);
    Material after = world.getBlockAt(markerX, markerY, markerZ).getType();
    int ores = count(world, chunkX, chunkZ, true);
    int nonAir = count(world, chunkX, chunkZ, false);
    plugin
      .getLogger()
      .info(
        "Regen self-test " +
        key.encode() +
        ": force=" +
        ok +
        ", markerBefore=" +
        before +
        ", markerAfter=" +
        after +
        " (cleared=" +
        (after != Material.DIAMOND_BLOCK) +
        "), oreBlocks=" +
        ores +
        ", nonAir=" +
        nonAir
      );
  }

  private static int count(
    World world,
    int chunkX,
    int chunkZ,
    boolean oresOnly
  ) {
    int minX = chunkX << 4;
    int minZ = chunkZ << 4;
    int count = 0;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
          Material material = world.getBlockAt(minX + x, y, minZ + z).getType();
          if (oresOnly) {
            if (isOre(material)) count++;
          } else if (!material.isAir()) {
            count++;
          }
        }
      }
    }
    return count;
  }

  private static boolean isOre(Material material) {
    return (
      material == Material.ANCIENT_DEBRIS || material.name().endsWith("_ORE")
    );
  }
}
