package dev.bisz.world.integration;

import java.util.Objects;

/** A chunk-aligned selection, typically produced from a WorldEdit region. */
public record SelectionRegion(
  String world,
  int minChunkX,
  int minChunkZ,
  int maxChunkX,
  int maxChunkZ
) {
  public SelectionRegion {
    Objects.requireNonNull(world, "world");
    if (maxChunkX < minChunkX || maxChunkZ < minChunkZ) {
      throw new IllegalArgumentException("Selection bounds are inverted");
    }
  }

  /** Number of chunks covered by this selection. */
  public long chunkCount() {
    return (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
  }
}
