package dev.bisz.world.wilderness;

import dev.bisz.world.WorldPlugin;
import java.util.Objects;

/** Selects the best available chunk regenerator without hard-failing. */
public final class Regenerators {

  private Regenerators() {}

  /**
   * Returns a WorldEdit-backed regenerator when WorldEdit is installed (for ore
   * stripping), otherwise a regenerator that only restores terrain.
   */
  public static ChunkRegenerator create(WorldPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null) {
      plugin
        .getLogger()
        .info(
          "WorldEdit is not installed; ore stripping and POI pasting are disabled."
        );
      return new NoopChunkRegenerator(plugin, plugin.settings());
    }
    try {
      ChunkRegenerator regenerator = new WorldEditChunkRegenerator(
        plugin,
        plugin.settings()
      );
      plugin
        .getLogger()
        .info("Wilderness regeneration: " + regenerator.name() + ".");
      return regenerator;
    } catch (LinkageError error) {
      plugin
        .getLogger()
        .warning(
          "WorldEdit API is incompatible; ore stripping is disabled: " +
          error.getMessage()
        );
      return new NoopChunkRegenerator(plugin, plugin.settings());
    }
  }
}
