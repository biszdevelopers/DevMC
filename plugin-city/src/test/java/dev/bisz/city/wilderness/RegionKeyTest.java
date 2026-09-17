package dev.bisz.city.wilderness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.bisz.city.model.ChunkKey;
import org.junit.jupiter.api.Test;

class RegionKeyTest {

  @Test
  void chunksGroupIntoSquareRegions() {
    assertEquals(
      new RegionKey("world", 0, 0),
      RegionKey.of(new ChunkKey("world", 0, 0), 4)
    );
    assertEquals(
      new RegionKey("world", 0, 0),
      RegionKey.of(new ChunkKey("world", 3, 3), 4)
    );
    assertEquals(
      new RegionKey("world", 1, 1),
      RegionKey.of(new ChunkKey("world", 4, 5), 4)
    );
  }

  @Test
  void negativeChunksFloorCorrectly() {
    assertEquals(
      new RegionKey("world", -1, -1),
      RegionKey.of(new ChunkKey("world", -1, -1), 4)
    );
    assertEquals(
      new RegionKey("world", -1, 0),
      RegionKey.of(new ChunkKey("world", -4, 2), 4)
    );
  }

  @Test
  void regionKeyRoundTrips() {
    RegionKey key = new RegionKey("world", -3, 7);
    assertEquals(key, RegionKey.decode(key.encode()));
  }
}
