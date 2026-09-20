package dev.bisz.worldgen.wilderness;

import dev.bisz.worldgen.WorldGenPlugin;
import dev.bisz.worldgen.snapshot.ChunkSnapshot;
import dev.bisz.worldgen.worldgen.WorldGenerators;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Regenerates a chunk by copying it from a scratch world generated with the same
 * seed and generator. This works even for chunks that cannot be unloaded (spawn
 * and player-loaded chunks), unlike region-file surgery or WorldEdit's
 * wrong-seed {@code regenerate}.
 */
public final class ScratchRegenerator {

  private ScratchRegenerator() {}

  /** Copies fresh terrain for a chunk from the scratch world into the target. */
  public static boolean regenerate(
    WorldGenPlugin plugin,
    World target,
    int chunkX,
    int chunkZ
  ) {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(target, "target");
    World scratch = scratchWorld(plugin, target);
    if (scratch == null) return false;
    Chunk source = scratch.getChunkAt(chunkX, chunkZ);
    ChunkSnapshot snapshot = ChunkSnapshot.capture(
      source,
      scratch.getMinHeight(),
      scratch.getMaxHeight() - 1
    );
    snapshot.restore(target, chunkX, chunkZ);
    // Save the scratch chunk so later cycles load it from disk instead of
    // paying full generation again (the previous save=false forced that).
    scratch.unloadChunk(chunkX, chunkZ, true);
    return true;
  }

  /** The scratch world for a target, created on demand with the same seed. */
  public static World scratchWorld(WorldGenPlugin plugin, World target) {
    String name = target.getName() + "_regen";
    World existing = Bukkit.getWorld(name);
    if (existing != null) return existing;
    WorldCreator creator = new WorldCreator(name)
      .seed(target.getSeed())
      .generateStructures(false)
      .generator(WorldGenerators.create(plugin.settings()));
    World created = creator.createWorld();
    if (created != null) {
      plugin
        .getLogger()
        .info(
          "Scratch regeneration world ready: " +
          created.getName() +
          " (seed " +
          target.getSeed() +
          ")."
        );
    }
    return created;
  }
}
