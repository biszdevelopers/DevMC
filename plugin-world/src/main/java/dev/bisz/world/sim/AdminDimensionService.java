package dev.bisz.world.sim;

import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.snapshot.ChunkSnapshot;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A dedicated storage and testing dimension. It is a flat, structure-free world
 * used to store structure blockstates, stage chunk snapshots, and run
 * regeneration simulations that do not depend on the live world type.
 */
public final class AdminDimensionService {

  /** Chunk used to stage simulated regenerations. */
  public static final int STAGING_CHUNK_X = 4096;
  public static final int STAGING_CHUNK_Z = 4096;

  private final JavaPlugin plugin;
  private final WorldSettings settings;
  private World world;

  public AdminDimensionService(JavaPlugin plugin, WorldSettings settings) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** Creates or adopts the admin dimension. */
  public World ensure() {
    if (world != null && world.isChunkLoaded(0, 0)) return world;
    String name = settings.storageDimension();
    World existing = Bukkit.getWorld(name);
    if (existing != null) {
      world = existing;
      return world;
    }
    World created = new WorldCreator(name)
      .type(WorldType.FLAT)
      .generateStructures(false)
      .environment(World.Environment.NORMAL)
      .createWorld();
    if (created == null) {
      plugin
        .getLogger()
        .warning("Could not create admin dimension " + name + ".");
      return null;
    }
    created.setSpawnLocation(0, created.getHighestBlockYAt(0, 0) + 1, 0);
    world = created;
    plugin.getLogger().info("Admin dimension ready: " + name + ".");
    return world;
  }

  /** The admin world, or null when it cannot be created. */
  public World world() {
    return world == null ? ensure() : world;
  }

  /** The staging origin used for simulations. */
  public Location stagingOrigin() {
    World target = world();
    if (target == null) return null;
    return new Location(target, STAGING_CHUNK_X << 4, 64, STAGING_CHUNK_Z << 4);
  }

  /** Clears a chunk in the admin dimension. */
  public void clearChunk(int chunkX, int chunkZ) {
    World target = world();
    if (target == null) return;
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = target.getMinHeight(); y < target.getMaxHeight(); y++) {
          target.getBlockAt(baseX + x, y, baseZ + z).setType(org.bukkit.Material.AIR, false);
        }
      }
    }
  }

  /** Restores a snapshot into the admin dimension. */
  public boolean restore(ChunkSnapshot snapshot, int chunkX, int chunkZ) {
    World target = world();
    if (target == null) return false;
    snapshot.restore(target, chunkX, chunkZ);
    return true;
  }

  /** Places a vanilla structure using the server's /place command. */
  public boolean placeVanillaStructure(String structureId, Location location) {
    Objects.requireNonNull(location, "location");
    if (location.getWorld() == null) return false;
    String command = String.format(
      "place structure %s %d %d %d",
      structureId,
      location.getBlockX(),
      location.getBlockY(),
      location.getBlockZ()
    );
    return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
  }

  /** Number of non-air blocks in a chunk, used for reporting. */
  public int nonAirBlocks(int chunkX, int chunkZ) {
    World target = world();
    if (target == null) return 0;
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    int count = 0;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = target.getMinHeight(); y < target.getMaxHeight(); y++) {
          Block block = target.getBlockAt(baseX + x, y, baseZ + z);
          if (!block.getType().isAir()) count++;
        }
      }
    }
    return count;
  }
}
