package dev.bisz.world.structure;

import dev.bisz.world.storage.Json;
import java.util.Map;
import java.util.Objects;

/** The world-space bounding box of a pasted structure. */
public record PlacedStructure(
  String world,
  int minX,
  int minY,
  int minZ,
  int maxX,
  int maxY,
  int maxZ
) {
  public PlacedStructure {
    Objects.requireNonNull(world, "world");
    if (maxX < minX || maxY < minY || maxZ < minZ) {
      throw new IllegalArgumentException("Placed structure bounds are inverted");
    }
  }

  /** Whether a block position falls inside these bounds. */
  public boolean contains(int x, int y, int z) {
    return (
      x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ
    );
  }

  /** Serializes these bounds into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    return Map.of(
      "world",
      world,
      "min_x",
      minX,
      "min_y",
      minY,
      "min_z",
      minZ,
      "max_x",
      maxX,
      "max_y",
      maxY,
      "max_z",
      maxZ
    );
  }

  /** Restores bounds from their stored representation. */
  public static PlacedStructure fromMap(Map<String, Object> map) {
    return new PlacedStructure(
      Json.string(map, "world", "world"),
      Json.integer(map, "min_x", 0),
      Json.integer(map, "min_y", 0),
      Json.integer(map, "min_z", 0),
      Json.integer(map, "max_x", 0),
      Json.integer(map, "max_y", 0),
      Json.integer(map, "max_z", 0)
    );
  }
}
