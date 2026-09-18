package dev.bisz.world.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

/**
 * Assigns one biome per angular sector of the rust-map island, with ocean
 * outside the island radius.
 */
public final class RustMapBiomeProvider extends BiomeProvider {

  private final int islandRadius;
  private final List<Biome> sectors;

  public RustMapBiomeProvider(int islandRadius, List<String> biomeKeys) {
    this.islandRadius = Math.max(1, islandRadius);
    List<Biome> resolved = new ArrayList<>();
    for (String key : biomeKeys) {
      Biome biome = Registry.BIOME.get(
        NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT))
      );
      if (biome != null) resolved.add(biome);
    }
    if (resolved.isEmpty()) resolved.add(Biome.PLAINS);
    this.sectors = List.copyOf(resolved);
  }

  @Override
  public Biome getBiome(WorldInfo info, int x, int y, int z) {
    double distance = Math.hypot(x, z);
    if (distance > islandRadius) return Biome.OCEAN;
    double angle = Math.atan2(z, x) + Math.PI;
    int index = (int) Math.floor((angle / (2.0 * Math.PI)) * sectors.size());
    if (index >= sectors.size()) index = sectors.size() - 1;
    if (index < 0) index = 0;
    return sectors.get(index);
  }

  @Override
  public List<Biome> getBiomes(WorldInfo info) {
    return sectors;
  }
}
