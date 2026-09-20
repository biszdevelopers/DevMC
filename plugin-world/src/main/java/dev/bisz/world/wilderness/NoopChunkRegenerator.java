package dev.bisz.world.wilderness;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.config.WorldSettings;
import java.util.Objects;
import org.bukkit.World;

/**
 * Regenerates chunks by copying fresh terrain from a same-seed scratch world.
 * Ore stripping is a no-op without WorldEdit.
 */
public final class NoopChunkRegenerator implements ChunkRegenerator {

  private final WorldPlugin plugin;

  public NoopChunkRegenerator(WorldPlugin plugin, WorldSettings settings) {
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
