package dev.bisz.world.snapshot;

import dev.bisz.world.storage.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * A compact, palette-encoded copy of a chunk's blockstates. Snapshots are used
 * to store structures, to move terrain into the admin dimension, and to run
 * regeneration simulations that do not depend on the live world type.
 */
public final class ChunkSnapshot {

  private static final int SIZE = 16;

  private final String world;
  private final int chunkX;
  private final int chunkZ;
  private final int minY;
  private final int maxY;
  private final List<String> palette;
  private final int[] indices;

  private ChunkSnapshot(
    String world,
    int chunkX,
    int chunkZ,
    int minY,
    int maxY,
    List<String> palette,
    int[] indices
  ) {
    this.world = world;
    this.chunkX = chunkX;
    this.chunkZ = chunkZ;
    this.minY = minY;
    this.maxY = maxY;
    this.palette = List.copyOf(palette);
    this.indices = indices;
  }

  /** Captures a live chunk between the supplied vertical bounds. */
  public static ChunkSnapshot capture(Chunk chunk, int minY, int maxY) {
    Objects.requireNonNull(chunk, "chunk");
    int low = Math.max(minY, chunk.getWorld().getMinHeight());
    int high = Math.min(maxY, chunk.getWorld().getMaxHeight() - 1);
    int sizeY = high - low + 1;
    // The native snapshot reads straight from the chunk's section arrays; the
    // old per-block chunk.getBlock() path was the dominant regeneration cost.
    org.bukkit.ChunkSnapshot nativeSnapshot = chunk.getChunkSnapshot(
      true,
      false,
      false
    );
    List<String> palette = new ArrayList<>();
    Map<String, Integer> lookup = new HashMap<>();
    int[] indices = new int[SIZE * SIZE * sizeY];
    for (int x = 0; x < SIZE; x++) {
      for (int z = 0; z < SIZE; z++) {
        for (int y = low; y <= high; y++) {
          String key = nativeSnapshot.getBlockData(x, y, z).getAsString();
          int index = lookup.computeIfAbsent(key, ignored -> {
            palette.add(key);
            return palette.size() - 1;
          });
          indices[offset(x, y - low, z, sizeY)] = index;
        }
      }
    }
    return new ChunkSnapshot(
      chunk.getWorld().getName(),
      chunk.getX(),
      chunk.getZ(),
      low,
      high,
      palette,
      indices
    );
  }

  /**
   * Builds a synthetic terrain-only snapshot so simulations work even on a
   * superflat server. Produces stone/deepslate with a dirt and grass surface.
   */
  public static ChunkSnapshot synthetic(
    int chunkX,
    int chunkZ,
    int minY,
    int maxY,
    int seaLevel,
    long seed
  ) {
    int sizeY = maxY - minY + 1;
    List<String> palette = new ArrayList<>(
      List.of(
        "minecraft:air",
        "minecraft:stone",
        "minecraft:deepslate",
        "minecraft:dirt",
        "minecraft:grass_block[snowy=false]",
        "minecraft:water[level=0]"
      )
    );
    Map<String, Integer> lookup = new HashMap<>();
    for (int index = 0; index < palette.size(); index++) {
      lookup.put(palette.get(index), index);
    }
    int[] indices = new int[SIZE * SIZE * sizeY];
    Random random = new Random(
      seed ^ (chunkX * 341873128712L) ^ (chunkZ * 132897987541L)
    );
    for (int x = 0; x < SIZE; x++) {
      for (int z = 0; z < SIZE; z++) {
        int worldX = (chunkX << 4) + x;
        int worldZ = (chunkZ << 4) + z;
        double hills =
          Math.sin(worldX * 0.05) * Math.cos(worldZ * 0.05) * 6.0 +
          (random.nextDouble() - 0.5) * 2.0;
        int height = (int) Math.round(seaLevel + hills);
        for (int y = minY; y <= maxY; y++) {
          String key = "minecraft:air";
          if (y <= height) {
            if (y <= height - 4) {
              key = y < 0 ? "minecraft:deepslate" : "minecraft:stone";
            } else if (y < height) {
              key = "minecraft:dirt";
            } else {
              key = "minecraft:grass_block[snowy=false]";
            }
          } else if (y <= seaLevel) {
            key = "minecraft:water[level=0]";
          }
          indices[offset(x, y - minY, z, sizeY)] = lookup.get(key);
        }
      }
    }
    return new ChunkSnapshot(
      "synthetic",
      chunkX,
      chunkZ,
      minY,
      maxY,
      palette,
      indices
    );
  }

  /** Places this snapshot at the supplied chunk coordinates. */
  public void restore(World world, int targetChunkX, int targetChunkZ) {
    Objects.requireNonNull(world, "world");
    BlockData[] parsed = new BlockData[palette.size()];
    int sizeY = sizeY();
    int baseX = targetChunkX << 4;
    int baseZ = targetChunkZ << 4;
    for (int x = 0; x < SIZE; x++) {
      for (int z = 0; z < SIZE; z++) {
        for (int y = 0; y < sizeY; y++) {
          int index = indices[offset(x, y, z, sizeY)];
          BlockData data = parsed[index];
          if (data == null) {
            data = Bukkit.createBlockData(palette.get(index));
            parsed[index] = data;
          }
          Block block = world.getBlockAt(baseX + x, minY + y, baseZ + z);
          block.setBlockData(data, false);
        }
      }
    }
  }

  public String world() {
    return world;
  }

  public int chunkX() {
    return chunkX;
  }

  public int chunkZ() {
    return chunkZ;
  }

  public int minY() {
    return minY;
  }

  public int maxY() {
    return maxY;
  }

  public int sizeY() {
    return maxY - minY + 1;
  }

  public int volume() {
    return SIZE * SIZE * sizeY();
  }

  /** Counts occurrences of each palette entry. */
  public Map<String, Integer> paletteCounts() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (int index : indices) {
      String key = palette.get(index);
      counts.merge(key, 1, Integer::sum);
    }
    return counts;
  }

  /** Number of blocks whose palette string contains the supplied token. */
  public int countContaining(String token) {
    int count = 0;
    for (int index : indices) {
      if (palette.get(index).contains(token)) count++;
    }
    return count;
  }

  /** Serializes this snapshot into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("world", world);
    map.put("chunk_x", chunkX);
    map.put("chunk_z", chunkZ);
    map.put("min_y", minY);
    map.put("max_y", maxY);
    map.put("palette", palette);
    List<Integer> flat = new ArrayList<>(indices.length);
    for (int index : indices) flat.add(index);
    map.put("indices", flat);
    return map;
  }

  /** Restores a snapshot from its stored representation. */
  public static ChunkSnapshot fromMap(Map<String, Object> map) {
    List<String> palette = Json.stringList(map, "palette");
    Object rawIndices = map.get("indices");
    if (!(rawIndices instanceof List<?> list) || palette.isEmpty()) {
      throw new IllegalArgumentException("Snapshot is missing palette or indices");
    }
    int[] indices = new int[list.size()];
    for (int index = 0; index < list.size(); index++) {
      Object value = list.get(index);
      indices[index] = value instanceof Number number ? number.intValue() : 0;
    }
    return new ChunkSnapshot(
      Json.string(map, "world", "unknown"),
      Json.integer(map, "chunk_x", 0),
      Json.integer(map, "chunk_z", 0),
      Json.integer(map, "min_y", 0),
      Json.integer(map, "max_y", 0),
      palette,
      indices
    );
  }

  private static int offset(int x, int y, int z, int sizeY) {
    return (y * SIZE + z) * SIZE + x;
  }
}
