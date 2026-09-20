package dev.bisz.world.wilderness;

import org.bukkit.World;

/** Strategy for restoring a wilderness chunk to fresh terrain. */
public interface ChunkRegenerator {

  /**
   * Regenerates a chunk from the world seed, restoring the exact barebone
   * terrain. Feature blocks (ores, plants, POIs, loot) are removed and must be
   * re-applied by the caller.
   *
   * @return true when the chunk was regenerated.
   */
  boolean regenerate(World world, int chunkX, int chunkZ);

  /**
   * Removes naturally generated ores so custom vein placement controls the
   * distribution. Returns false when the backend cannot strip ores.
   */
  default boolean stripOres(World world, int chunkX, int chunkZ) {
    return false;
  }

  /** A human-readable name for logging. */
  String name();
}
