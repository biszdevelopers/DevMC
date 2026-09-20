package dev.bisz.world.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BiomeClimateTableTest {

  private static final List<String> BIOMES = List.of(
    "plains",
    "forest",
    "birch_forest",
    "taiga",
    "snowy_plains",
    "desert",
    "savanna",
    "jungle",
    "swamp",
    "badlands",
    "windswept_hills",
    "stony_peaks"
  );

  private static final List<String> CLIMATE_BIOMES = List.of(
    "plains",
    "forest",
    "birch_forest",
    "taiga",
    "snowy_plains",
    "desert",
    "savanna",
    "jungle",
    "swamp",
    "badlands"
  );

  private BiomeClimateTable table() {
    return new BiomeClimateTable(BIOMES);
  }

  @Test
  void eachTargetWinsAtItsOwnClimate() {
    BiomeClimateTable table = table();
    for (BiomeClimateTable.Target target : table.targets()) {
      assertEquals(
        target.biome(),
        table.nearest(
          target.temperature(),
          target.humidity(),
          target.erosion()
        )
      );
    }
  }

  @Test
  void climateBiomesCoverTheClimateSquare() {
    BiomeClimateTable table = table();
    Set<String> found = new HashSet<>();
    for (double erosion = -1.0; erosion <= 1.0; erosion += 0.25) {
      for (double temperature = -1.0; temperature <= 1.0; temperature += 0.05) {
        for (double humidity = -1.0; humidity <= 1.0; humidity += 0.05) {
          found.add(table.nearest(temperature, humidity, erosion));
        }
      }
    }
    for (String biome : CLIMATE_BIOMES) {
      assertTrue(found.contains(biome), "missing " + biome + " in " + found);
    }
  }

  @Test
  void lowErosionSelectsMountains() {
    BiomeClimateTable table = table();
    String peak = table.nearest(0.0, 0.0, -1.0);
    assertTrue(
      peak.equals("stony_peaks") || peak.equals("windswept_hills"),
      "expected a mountain biome, got " + peak
    );
  }
}
