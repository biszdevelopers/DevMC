package dev.bisz.world.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps a climate point to the nearest configured biome. Temperature and
 * humidity decide the climate biomes; erosion decides mountains, so mountain
 * biomes only win where the terrain is actually high. The biomes are spread
 * across climate space, so a map whose climate spans the full range visits
 * every one.
 */
public final class BiomeClimateTable {

  /** One biome's ideal climate. */
  public record Target(
    String biome,
    double temperature,
    double humidity,
    double erosion
  ) {}

  private static final double WEIGHT_TEMPERATURE = 1.0;
  private static final double WEIGHT_HUMIDITY = 1.0;
  private static final double WEIGHT_EROSION = 1.4;

  private final List<Target> targets;

  public BiomeClimateTable(List<String> biomeKeys) {
    List<Target> built = new ArrayList<>();
    for (String key : biomeKeys) {
      if (key != null && !key.isBlank()) built.add(target(key));
    }
    if (built.isEmpty()) built.add(target("plains"));
    this.targets = List.copyOf(built);
  }

  public List<Target> targets() {
    return targets;
  }

  /** The configured biome whose climate target is nearest the supplied point. */
  public String nearest(
    double temperature,
    double humidity,
    double erosion
  ) {
    String best = targets.get(0).biome();
    double bestDistance = Double.MAX_VALUE;
    for (Target target : targets) {
      double dt = temperature - target.temperature();
      double dh = humidity - target.humidity();
      double de = erosion - target.erosion();
      double distance =
        WEIGHT_TEMPERATURE * dt * dt +
        WEIGHT_HUMIDITY * dh * dh +
        WEIGHT_EROSION * de * de;
      if (distance < bestDistance) {
        bestDistance = distance;
        best = target.biome();
      }
    }
    return best;
  }

  private static Target target(String key) {
    String biome = key.toLowerCase(Locale.ROOT);
    return switch (biome) {
      case "snowy_plains" -> new Target(biome, -0.65, -0.25, 0.2);
      case "taiga" -> new Target(biome, -0.6, 0.35, 0.1);
      case "plains" -> new Target(biome, 0.0, -0.3, 0.2);
      case "forest" -> new Target(biome, 0.0, 0.3, 0.1);
      case "birch_forest" -> new Target(biome, 0.2, 0.55, 0.0);
      case "swamp" -> new Target(biome, 0.3, 0.75, -0.2);
      case "savanna" -> new Target(biome, 0.55, -0.1, 0.2);
      case "jungle" -> new Target(biome, 0.6, 0.6, -0.1);
      case "desert" -> new Target(biome, 0.8, -0.5, 0.15);
      case "badlands" -> new Target(biome, 0.8, -0.5, -0.4);
      case "windswept_hills" -> new Target(biome, 0.0, 0.0, -0.75);
      case "stony_peaks" -> new Target(biome, 0.0, -0.1, -1.0);
      default -> new Target(biome, 0.0, 0.0, 0.0);
    };
  }
}
