package dev.bisz.world.wilderness;

import dev.bisz.world.loot.LootTables;
import dev.bisz.world.model.LootTable;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;

/**
 * The built-in destructible POI: a vanilla-style dungeon — a mossy cobblestone
 * room with a spawner, one or two loot chests, and the occasional cobweb. Kept
 * within a single chunk so the next regeneration of that chunk cleanly erases
 * it. Used as a fallback when no POI schematics are registered.
 */
public final class SmallStructures {

  /** Loot table used by the built-in dungeon. */
  public static final String DUNGEON_TABLE = "tier1";

  private final LootTables tables;

  public SmallStructures(LootTables tables) {
    this.tables = Objects.requireNonNull(tables, "tables");
  }

  /** Places the built-in dungeon in this chunk. */
  public void place(World world, int chunkX, int chunkZ, Random random) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(random, "random");
    int half = 2 + random.nextInt(2);
    int margin = half + 2;
    int minX = chunkX << 4;
    int minZ = chunkZ << 4;
    int anchorX = minX + margin + random.nextInt(16 - margin * 2);
    int anchorZ = minZ + margin + random.nextInt(16 - margin * 2);
    int worldMin = world.getMinHeight();
    int worldMax = world.getMaxHeight() - 1;
    int low = Math.max(worldMin + 8, -40);
    int high = Math.min(worldMax - 8, 40);
    if (high <= low) return;
    int floorY = low + random.nextInt(high - low + 1);
    if (world.getBlockAt(anchorX, floorY, anchorZ).getType() == Material.AIR) {
      return;
    }

    for (int dx = -half - 1; dx <= half + 1; dx++) {
      for (int dz = -half - 1; dz <= half + 1; dz++) {
        boolean edge =
          Math.abs(dx) == half + 1 || Math.abs(dz) == half + 1;
        for (int dy = -1; dy <= 4; dy++) {
          Block block = world.getBlockAt(anchorX + dx, floorY + dy, anchorZ + dz);
          if (edge || dy == -1 || dy == 4) {
            Material wall = random.nextDouble() < 0.3
              ? Material.MOSSY_COBBLESTONE
              : Material.COBBLESTONE;
            block.setType(wall, false);
          } else {
            block.setType(Material.AIR, false);
          }
        }
      }
    }

    Block spawnerBlock = world.getBlockAt(anchorX, floorY, anchorZ);
    spawnerBlock.setType(Material.SPAWNER, false);
    if (spawnerBlock.getState() instanceof CreatureSpawner spawner) {
      spawner.setSpawnedType(EntityType.ZOMBIE);
      spawner.update(true, false);
    }

    int chests = 1 + random.nextInt(2);
    for (int index = 0; index < chests; index++) {
      int chestX = anchorX + random.nextInt(half * 2 + 1) - half;
      int chestZ = anchorZ + random.nextInt(half * 2 + 1) - half;
      if (chestX == anchorX && chestZ == anchorZ) continue;
      Block chestBlock = world.getBlockAt(chestX, floorY, chestZ);
      if (chestBlock.getType() != Material.AIR) continue;
      chestBlock.setType(Material.CHEST, false);
      if (chestBlock.getState() instanceof Chest chest) {
        LootTable table = tables.get(DUNGEON_TABLE);
        if (table != null) {
          LootReseeder.fill(chest.getInventory(), table, random);
        }
        chest.update(true, false);
      }
    }

    if (random.nextDouble() < 0.5) {
      for (int index = 0; index < 2; index++) {
        int webX = anchorX + random.nextInt(half * 2 + 1) - half;
        int webZ = anchorZ + random.nextInt(half * 2 + 1) - half;
        int webY = floorY + random.nextInt(3);
        Block web = world.getBlockAt(webX, webY, webZ);
        if (web.getType() == Material.AIR) {
          web.setType(Material.COBWEB, false);
        }
      }
    }
  }
}
