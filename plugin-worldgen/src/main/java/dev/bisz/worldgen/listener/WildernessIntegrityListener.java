package dev.bisz.worldgen.listener;

import dev.bisz.worldgen.WorldGenPlugin;
import dev.bisz.worldgen.model.ChunkKey;
import dev.bisz.worldgen.wilderness.ChunkState;
import dev.bisz.worldgen.wilderness.ScratchRegenerator;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * The safety net of wilderness change detection. Most changes arrive as events
 * ({@link WildernessListener}); this backstop catches anything those miss (other
 * plugins, mods) by comparing a few terrain columns against the same-seed
 * scratch world when a tracked chunk unloads. A chunk cannot change while
 * unloaded, so a chunk that still matches the scratch terrain is unchanged.
 */
public final class WildernessIntegrityListener implements Listener {

  /** Column spacing of the sampled grid; 4 checks 16 columns per chunk. */
  private static final int STRIDE = 4;

  private final WorldGenPlugin plugin;

  public WildernessIntegrityListener(WorldGenPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onUnload(ChunkUnloadEvent event) {
    if (!plugin.settings().regenVerifyChanges()) return;
    Chunk chunk = event.getChunk();
    World world = chunk.getWorld();
    ChunkKey key = new ChunkKey(world.getName(), chunk.getX(), chunk.getZ());
    if (!plugin.settings().managesWorld(key.world())) return;
    ChunkState state = plugin.indicators().get(key);
    if (state == null) return;
    if (hasMissingTerrain(world, chunk)) {
      long now = System.currentTimeMillis();
      state.markEdit(now);
      plugin
        .getLogger()
        .info("Integrity check marked " + key.encode() + " dirty");
    }
  }

  /**
   * Samples columns and reports whether the live surface terrain is missing
   * where the scratch world has solid terrain.
   */
  private boolean hasMissingTerrain(World world, Chunk chunk) {
    World scratch = ScratchRegenerator.scratchWorld(plugin, world);
    if (scratch == null) return false;
    if (!scratch.isChunkGenerated(chunk.getX(), chunk.getZ())) return false;
    int baseX = chunk.getX() << 4;
    int baseZ = chunk.getZ() << 4;
    for (int dx = 0; dx < 16; dx += STRIDE) {
      for (int dz = 0; dz < 16; dz += STRIDE) {
        int x = baseX + dx;
        int z = baseZ + dz;
        int y = scratch.getHighestBlockYAt(x, z);
        if (scratch.getBlockAt(x, y, z).getType().isAir()) continue;
        if (world.getBlockAt(x, y, z).getType().isAir()) return true;
      }
    }
    return false;
  }
}
