package dev.bisz.worldgen.model;

import java.util.Objects;
import org.bukkit.Chunk;
import org.bukkit.Location;

/** The world-and-coordinate identity of a chunk. The atomic unit of land. */
public record ChunkKey(String world, int x, int z) {

  public ChunkKey {
    Objects.requireNonNull(world, "world");
  }

  /** Creates a key for a loaded chunk. */
  public static ChunkKey of(Chunk chunk) {
    Objects.requireNonNull(chunk, "chunk");
    return new ChunkKey(
      chunk.getWorld().getName(),
      chunk.getX(),
      chunk.getZ()
    );
  }

  /** Creates a key for the chunk containing a location. */
  public static ChunkKey of(Location location) {
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(location.getWorld(), "location world");
    return new ChunkKey(
      location.getWorld().getName(),
      location.getBlockX() >> 4,
      location.getBlockZ() >> 4
    );
  }

  /** Encodes this key as a stable single-token string. */
  public String encode() {
    return world + ":" + x + ":" + z;
  }

  /** Decodes a key produced by {@link #encode()}. */
  public static ChunkKey decode(String value) {
    Objects.requireNonNull(value, "value");
    int last = value.lastIndexOf(':');
    int middle = value.lastIndexOf(':', last - 1);
    if (middle <= 0 || last <= middle + 1) {
      throw new IllegalArgumentException("Invalid chunk key: " + value);
    }
    return new ChunkKey(
      value.substring(0, middle),
      Integer.parseInt(value.substring(middle + 1, last)),
      Integer.parseInt(value.substring(last + 1))
    );
  }

  @Override
  public String toString() {
    return encode();
  }
}
