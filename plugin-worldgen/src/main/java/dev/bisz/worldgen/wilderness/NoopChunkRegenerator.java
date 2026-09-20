package dev.bisz.worldgen.wilderness;

import dev.bisz.worldgen.WorldGenPlugin;
import dev.bisz.worldgen.config.WorldSettings;
import java.util.Objects;
import org.bukkit.World;

/**
 * Regenerates chunks by copying fresh terrain from a same-seed scratch world.
 * Ore stripping is a no-op without WorldEdit.
 */
public final class NoopChunkRegenerator implements ChunkRegenerator {

  private final WorldGenPlugin plugin;

  public NoopChunkRegenerator(WorldGenPlugin plugin, WorldSettings settings) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(settings, "settings");
  }

  @Override
  public boolean regenerate(World world, int chunkX, int chunkZ) {
    return ScratchRegenerator.regenerate(plugin, world, chunkX, chunkZ);
  }

  @Override
  public String name() {
    return "scratch";
  }
}
