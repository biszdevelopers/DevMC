package dev.bisz.world.worldgen;

import dev.bisz.world.structure.StructureDefinition;
import dev.bisz.world.structure.StructureService;
import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Places one copy of each registered structure at evenly spaced anchors around
 * the rust-map island, using the plugin's own structure system rather than
 * vanilla generation.
 */
public final class RustMapPlanter {

  private RustMapPlanter() {}

  /**
   * Plants every registered structure around the island.
   *
   * @return the number of structures placed
   */
  public static int plant(
    World world,
    StructureService structures,
    int anchorRadius
  ) {
    Collection<StructureDefinition> definitions =
      structures.registry().all();
    if (definitions.isEmpty()) return 0;
    int placed = 0;
    int index = 0;
    for (StructureDefinition definition : definitions) {
      double angle = (2.0 * Math.PI * index) / definitions.size();
      int x = (int) Math.round(Math.cos(angle) * anchorRadius);
      int z = (int) Math.round(Math.sin(angle) * anchorRadius);
      int y = world.getHighestBlockYAt(x, z) + 1;
      Location anchor = new Location(world, x + 0.5, y, z + 0.5);
      if (structures.spawn(definition.id(), anchor, "rustmap") != null) {
        placed++;
      }
      index++;
    }
    return placed;
  }
}
