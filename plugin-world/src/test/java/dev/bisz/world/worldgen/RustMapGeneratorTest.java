package dev.bisz.world.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RustMapGeneratorTest {

  private RustMapGenerator generator() {
    return new RustMapGenerator(
      1000,
      62,
      List.of("plains", "desert", "taiga", "jungle")
    );
  }

  @Test
  void islandCentreIsHigherThanTheEdge() {
    RustMapGenerator generator = generator();
    int centre = generator.height(0, 0, 1L);
    int edge = generator.height(980, 0, 1L);
    assertTrue(centre > edge, "centre " + centre + " should exceed edge " + edge);
  }

  @Test
  void outsideTheRadiusTerrainDropsToTheSeaFloor() {
    RustMapGenerator generator = generator();
    int outside = generator.height(5000, 0, 1L);
    assertEquals(generator.seaLevel() - 8, outside);
  }

  @Test
  void radiusAndSeaLevelAreRetained() {
    RustMapGenerator generator = generator();
    assertEquals(1000, generator.islandRadius());
    assertEquals(62, generator.seaLevel());
  }

  @Test
  void terrainIsDeterministicForTheSameSeed() {
    RustMapGenerator generator = generator();
    assertEquals(
      generator.height(120, -240, 99L),
      generator.height(120, -240, 99L)
    );
  }
}
