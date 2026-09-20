package dev.bisz.world.wilderness;

import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.economy.Contraband;
import dev.bisz.world.loot.LootTables;
import dev.bisz.world.model.LootTable;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Scatters hidden loot caches across regenerated wilderness chunks. */
public final class LootReseeder {

  /** The loot table used for generic wilderness caches. */
  public static final String WILDERNESS_TABLE = "wilderness";

  private final WorldSettings settings;
  private final LootTables tables;

  public LootReseeder(WorldSettings settings, LootTables tables) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.tables = Objects.requireNonNull(tables, "tables");
  }

  /**
   * Places and fills this chunk's loot caches.
   *
   * @return the number of caches placed, used as part of the resource baseline
   */
  public int reseed(World world, int chunkX, int chunkZ, Random random) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(random, "random");
    LootTable table = tables.get(WILDERNESS_TABLE);
    if (table == null) return 0;
    int caches = settings.lootCachesPerChunk();
    if (caches <= 0) return 0;
    int minX = chunkX << 4;
    int minZ = chunkZ << 4;
    int worldMin = world.getMinHeight();
    int worldMax = world.getMaxHeight() - 1;
    int low = Math.max(worldMin + 5, 5);
    int high = Math.min(worldMax - 1, 48);
    if (high <= low) return 0;
    int placed = 0;
    for (int cache = 0; cache < caches; cache++) {
      int x = minX + random.nextInt(16);
      int z = minZ + random.nextInt(16);
      int y = low + random.nextInt(high - low + 1);
      Block block = world.getBlockAt(x, y, z);
      Material current = block.getType();
      if (current != Material.STONE && current != Material.DEEPSLATE) continue;
      block.setType(Material.CHEST, false);
      if (block.getState() instanceof Chest chest) {
        fill(chest.getInventory(), table, random);
        chest.update(true, false);
      }
      placed++;
    }
    return placed;
  }

  /** Fills an inventory with a table's rolls, tagging contraband. */
  public static void fill(
    Inventory inventory,
    LootTable table,
    Random random
  ) {
    inventory.clear();
    for (LootTable.LootStack stack : table.roll(random)) {
      Material material = Material.matchMaterial(stack.itemId());
      if (material == null || material.isAir()) continue;
      int remaining = stack.amount();
      while (remaining > 0) {
        int amount = Math.min(remaining, material.getMaxStackSize());
        ItemStack item = new ItemStack(material, amount);
        if (stack.contraband()) Contraband.mark(item);
        inventory.addItem(item);
        remaining -= amount;
      }
    }
  }
}
