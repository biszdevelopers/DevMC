package dev.bisz.city.wilderness;

import dev.bisz.bundler.JSON;
import dev.bisz.city.loot.LootTables;
import dev.bisz.city.model.ChunkKey;
import dev.bisz.city.model.LootTable;
import dev.bisz.city.model.Monument;
import dev.bisz.city.storage.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.plugin.java.JavaPlugin;

/** Registry of persistent monuments and their loot respawn timers. */
public final class MonumentManager {

  /** ServerData-relative monument document. */
  public static final String FILE = "city/monuments.json";

  private final JavaPlugin plugin;
  private final LootTables tables;
  private final Map<String, Monument> monuments = new LinkedHashMap<>();
  private final Random random = new Random();

  public MonumentManager(JavaPlugin plugin, LootTables tables) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.tables = Objects.requireNonNull(tables, "tables");
  }

  /** Loads every monument from ServerData. */
  public void load() {
    monuments.clear();
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    for (Map<String, Object> raw : Json.mapList(document, "monuments")) {
      try {
        Monument monument = Monument.fromMap(raw);
        monuments.put(monument.id(), monument);
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning(
            "Skipping malformed monument entry: " + exception.getMessage()
          );
      }
    }
  }

  /** Persists every monument to ServerData. */
  public void save() {
    List<Map<String, Object>> encoded = new ArrayList<>();
    for (Monument monument : monuments.values()) encoded.add(monument.toMap());
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schema", 1);
    document.put("monuments", encoded);
    JSON.saveDataFromDataBase(FILE, document);
  }

  public Collection<Monument> all() {
    return List.copyOf(monuments.values());
  }

  public Monument get(String id) {
    return monuments.get(id);
  }

  /** Registers or replaces a monument. */
  public void add(Monument monument) {
    Objects.requireNonNull(monument, "monument");
    monuments.put(monument.id(), monument);
    save();
  }

  /** Removes a monument, returning whether it existed. */
  public boolean remove(String id) {
    boolean removed = monuments.remove(id) != null;
    if (removed) save();
    return removed;
  }

  /** Whether a chunk intersects any monument and must be exempt from reset. */
  public boolean isExempt(ChunkKey key) {
    int minX = key.x() << 4;
    int minZ = key.z() << 4;
    int maxX = minX + 15;
    int maxZ = minZ + 15;
    for (Monument monument : monuments.values()) {
      if (!monument.world().equals(key.world())) continue;
      if (
        minX <= monument.maxX() &&
        maxX >= monument.minX() &&
        minZ <= monument.maxZ() &&
        maxZ >= monument.minZ()
      ) return true;
    }
    return false;
  }

  /** Refills any monument whose loot timer has elapsed. */
  public void tick(long now) {
    for (Monument monument : monuments.values()) {
      long respawnMillis = monument.respawnTicks() * 50L;
      if (now - monument.lastLootedAt() < respawnMillis) continue;
      refill(monument, now);
    }
  }

  private void refill(Monument monument, long now) {
    World world = Bukkit.getWorld(monument.world());
    if (world == null) return;
    LootTable table = tables.get(monument.lootTableId());
    if (table == null) return;
    int x = (monument.minX() + monument.maxX()) / 2;
    int z = (monument.minZ() + monument.maxZ()) / 2;
    int y = monument.minY() + 1;
    if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
    Block block = world.getBlockAt(x, y, z);
    if (!(block.getState() instanceof Chest)) {
      block.setType(Material.CHEST, false);
    }
    if (block.getState() instanceof Chest chest) {
      LootReseeder.fill(chest.getInventory(), table, random);
      chest.update(true, false);
    }
    monument.lastLootedAt(now);
    save();
  }
}
