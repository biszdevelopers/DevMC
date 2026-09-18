package dev.bisz.world.wilderness;

import org.bukkit.World;

/** Safe fallback that never touches terrain. */
public final class NoopChunkRegenerator implements ChunkRegenerator {

  @Override
  public boolean regenerate(World world, int chunkX, int chunkZ) {
    return false;
  }

  @Override
  public String name() {
    return "none";
  }
}
