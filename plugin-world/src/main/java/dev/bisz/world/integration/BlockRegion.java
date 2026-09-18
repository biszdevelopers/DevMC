package dev.bisz.world.integration;

import java.util.Objects;

/** A world-space block selection, typically produced from a WorldEdit region. */
public record BlockRegion(
  String world,
  int minX,
  int minY,
  int minZ,
  int maxX,
  int maxY,
  int maxZ
) {
  public BlockRegion {
    Objects.requireNonNull(world, "world");
    if (maxX < minX || maxY < minY || maxZ < minZ) {
      throw new IllegalArgumentException("Block region bounds are inverted");
    }
  }
}
