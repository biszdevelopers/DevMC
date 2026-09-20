package dev.bisz.world.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkSnapshotTest {

  private ChunkSnapshot synthetic() {
    return ChunkSnapshot.synthetic(3, -2, -64, 128, 62, 12345L);
  }

  @Test
  void syntheticSnapshotCoversTheVolume() {
    ChunkSnapshot snapshot = synthetic();
    assertEquals(16 * 16 * (128 - (-64) + 1), snapshot.volume());
    assertEquals(3, snapshot.chunkX());
    assertEquals(-2, snapshot.chunkZ());
  }

  @Test
  void syntheticTerrainContainsStoneAndAir() {
    Map<String, Integer> counts = synthetic().paletteCounts();
    assertTrue(counts.containsKey("minecraft:stone"));
    assertTrue(counts.containsKey("minecraft:air"));
    assertTrue(counts.getOrDefault("minecraft:stone", 0) > 0);
  }

  @Test
  void snapshotRoundTripsThroughMap() {
    ChunkSnapshot original = synthetic();
    ChunkSnapshot restored = ChunkSnapshot.fromMap(original.toMap());
    assertEquals(original.volume(), restored.volume());
    assertEquals(original.minY(), restored.minY());
    assertEquals(original.maxY(), restored.maxY());
    assertEquals(original.paletteCounts(), restored.paletteCounts());
  }

  @Test
  void countContainingFindsOreTokens() {
    ChunkSnapshot snapshot = synthetic();
    assertEquals(0, snapshot.countContaining("_ore"));
  }
}
