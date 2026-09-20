package dev.bisz.world.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IslandMaskTest {

  private IslandMask mask() {
    return new IslandMask(2048, 0.15, 96);
  }

  @Test
  void coreIsUntouched() {
    IslandMask mask = mask();
    assertEquals(1.0, mask.falloff(0, 0), 1.0E-9);
    assertEquals(1.0, mask.falloff(mask.landRadius() - 1, 0), 1.0E-9);
  }

  @Test
  void edgeFallsToOcean() {
    IslandMask mask = mask();
    assertEquals(0.0, mask.falloff(mask.islandRadius() + 10, 0), 1.0E-9);
    assertTrue(mask.carvedOcean(mask.islandRadius() + 10, 0, 0.12));
  }

  @Test
  void falloffIsMonotonic() {
    IslandMask mask = mask();
    double previous = 1.0;
    for (int x = mask.landRadius(); x <= mask.islandRadius(); x += 4) {
      double falloff = mask.falloff(x, 0);
      assertTrue(falloff <= previous + 1.0E-9, "falloff must not increase");
      previous = falloff;
    }
    assertTrue(previous < 0.05, "edge falloff should approach zero");
  }

  @Test
  void radiusHonoursOceanMargin() {
    assertEquals(1024 - 96, mask().islandRadius());
  }
}
