package dev.bisz.city.wilderness;

import dev.bisz.city.model.ChunkKey;
import java.util.Objects;

/**
 * A group of chunks that share regeneration indicators.
 *
 * <p>Regions are square cells of {@code regionSize} chunks. Indicators are
 * tracked per region so scheduling decisions are cheap and stable, while
 * regeneration still executes per chunk within the tick budget.
 */
public record RegionKey(String world, int x, int z) {

  public RegionKey {
    Objects.requireNonNull(world, "world");
  }

  /** The region containing a chunk. */
  public static RegionKey of(ChunkKey chunk, int regionSize) {
    Objects.requireNonNull(chunk, "chunk");
    int size = Math.max(1, regionSize);
    return new RegionKey(
      chunk.world(),
      Math.floorDiv(chunk.x(), size),
      Math.floorDiv(chunk.z(), size)
    );
  }

  /** Encodes this key as a stable single-token string. */
  public String encode() {
    return world + ":" + x + ":" + z;
  }

  /** Decodes a key produced by {@link #encode()}. */
  public static RegionKey decode(String value) {
    Objects.requireNonNull(value, "value");
    int last = value.lastIndexOf(':');
    int middle = value.lastIndexOf(':', last - 1);
    if (middle <= 0 || last <= middle + 1) {
      throw new IllegalArgumentException("Invalid region key: " + value);
    }
    return new RegionKey(
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
