package dev.bisz.worldgen.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChunkKeyTest {

  @Test
  void encodesAndDecodes() {
    ChunkKey key = new ChunkKey("world", -12, 34);
    assertEquals(key, ChunkKey.decode(key.encode()));
  }

  @Test
  void decodesWorldNamesWithColons() {
    ChunkKey key = new ChunkKey("minecraft:overworld", 5, -7);
    assertEquals(key, ChunkKey.decode(key.encode()));
  }
}
