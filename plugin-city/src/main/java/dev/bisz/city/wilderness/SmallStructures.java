package dev.bisz.city.wilderness;

import dev.bisz.city.config.CitySettings;
import dev.bisz.city.loot.LootTables;
import dev.bisz.city.model.LootTable;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;

/**
 * Places destructible small structures at semi-random locations during
 * regeneration. Large landmarks are monuments and are never placed here.
 *
 * <p>Structures are kept within a single chunk so the next regeneration of that
 * chunk cleanly erases them.
 */
public final class SmallStructures {

  /** Loot table used by the built-in dungeon. */
  public static final String DUNGEON_TABLE = "tier1";

  private static final int DUNGEON_RADIUS = 3;
  private static final int DUNGEON_HEIGHT = 4;

  private final CitySettings settings;
  private final LootTables tables;

  public SmallStructures(CitySettings settings, LootTables tables) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.tables = Objects.requireNonNull(tables, "tables");
  }

  /** Rolls and places a small structure in this chunk, if any. */
  public void reseed(World world, int chunkX, int chunkZ, Random random) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(random, "random");
    if (settings.smallStructureChance() <= 0.0) return;
    if (random.nextDouble() > settings.smallStructureChance()) return;
    buildDungeon(world, chunkX, chunkZ, random);
  }

  private void buildDungeon(
    World world,
    int chunkX,
    int chunkZ,
    Random random
  ) {
    int minX = chunkX << 4;
    int minZ = chunkZ << 4;
    int anchorX =
      minX + DUNGEON_RADIUS + 1 + random.nextInt(16 - DUNGEON_RADIUS * 2 - 2);
    int anchorZ =
      minZ + DUNGEON_RADIUS + 1 + random.nextInt(16 - DUNGEON_RADIUS * 2 - 2);
    int worldMin = world.getMinHeight();
    int worldMax = world.getMaxHeight() - 1;
    int low = Math.max(worldMin + DUNGEON_HEIGHT + 2, -40);
    int high = Math.min(worldMax - DUNGEON_HEIGHT - 2, 40);
    if (high <= low) return;
    int anchorY = low + random.nextInt(high - low + 1);
    Block center = world.getBlockAt(anchorX, anchorY, anchorZ);
    if (center.getType() == Material.AIR) return;

    int floor = anchorY - 1;
    int ceiling = anchorY + DUNGEON_HEIGHT;
    for (int dx = -DUNGEON_RADIUS; dx <= DUNGEON_RADIUS; dx++) {
      for (int dz = -DUNGEON_RADIUS; dz <= DUNGEON_RADIUS; dz++) {
        boolean edge =
          Math.abs(dx) == DUNGEON_RADIUS || Math.abs(dz) == DUNGEON_RADIUS;
        world
          .getBlockAt(anchorX + dx, floor, anchorZ + dz)
          .setType(Material.COBBLESTONE, false);
        world
          .getBlockAt(anchorX + dx, ceiling, anchorZ + dz)
          .setType(Material.COBBLESTONE, false);
        for (int dy = 0; dy < DUNGEON_HEIGHT; dy++) {
          Block block = world.getBlockAt(
            anchorX + dx,
            anchorY + dy,
            anchorZ + dz
          );
          if (edge) {
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

    Block spawnerBlock = world.getBlockAt(anchorX, anchorY, anchorZ);
    spawnerBlock.setType(Material.SPAWNER, false);
    if (spawnerBlock.getState() instanceof CreatureSpawner spawner) {
      spawner.setSpawnedType(EntityType.ZOMBIE);
      spawner.update(true, false);
    }

    Block chestBlock = world.getBlockAt(anchorX + 1, anchorY, anchorZ);
    chestBlock.setType(Material.CHEST, false);
    if (chestBlock.getState() instanceof Chest chest) {
      LootTable table = tables.get(DUNGEON_TABLE);
      if (table != null) {
        LootReseeder.fill(chest.getInventory(), table, random);
      }
      chest.update(true, false);
    }
  }
}
