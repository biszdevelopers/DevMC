package dev.bisz.worldgen.structure;

import dev.bisz.worldgen.model.LootTable;
import dev.bisz.worldgen.wilderness.LootReseeder;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;

/** Fills the containers of a pasted structure with a loot table. */
public final class StructureLoot {

  private StructureLoot() {}

  /**
   * Fills every container inside the bounds.
   *
   * @return the number of containers filled
   */
  public static int fill(
    World world,
    PlacedStructure bounds,
    LootTable table,
    Random random
  ) {
    if (world == null || table == null) return 0;
    int filled = 0;
    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
      for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
          Block block = world.getBlockAt(x, y, z);
          if (!isContainerMaterial(block.getType())) continue;
          if (block.getState() instanceof Container container) {
            LootReseeder.fill(container.getInventory(), table, random);
            filled++;
          }
        }
      }
    }
    return filled;
  }

  private static boolean isContainerMaterial(Material material) {
    if (material == null || material.isAir()) return false;
    String name = material.name();
    return (
      name.contains("CHEST") ||
      name.contains("BARREL") ||
      name.contains("SHULKER_BOX") ||
      name.contains("FURNACE") ||
      name.contains("HOPPER") ||
      name.contains("DISPENSER") ||
      name.contains("DROPPER")
    );
  }
}
