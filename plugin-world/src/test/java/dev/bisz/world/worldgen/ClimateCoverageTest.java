package dev.bisz.world.worldgen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClimateCoverageTest {

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

  private static final double ISLAND_RADIUS = 928;
  private static final double LAND_RADIUS = 789;

  @Test
  void climateFieldCoversEveryClimateBiome() {
    BiomeClimateTable table = new BiomeClimateTable(BIOMES);
    ClimateField field = new ClimateField(
      42L,
      ISLAND_RADIUS,
      0.6,
      0.0016,
      120.0,
      4
    );
    Set<String> found = new HashSet<>();
    double[] erosions = { -1.0, -0.4, 0.0, 0.4, 1.0 };
    for (int x = (int) -LAND_RADIUS; x <= LAND_RADIUS; x += 12) {
      for (int z = (int) -LAND_RADIUS; z <= LAND_RADIUS; z += 12) {
        if (Math.hypot(x, z) > LAND_RADIUS) continue;
        double temperature = field.temperature(x, z, 0.0);
        double humidity = field.humidity(x, z, 0.0);
        for (double erosion : erosions) {
          found.add(table.nearest(temperature, humidity, erosion));
        }
      }
    }
    for (String biome : CLIMATE_BIOMES) {
      assertTrue(found.contains(biome), "missing " + biome + " in " + found);
    }
  }
}
