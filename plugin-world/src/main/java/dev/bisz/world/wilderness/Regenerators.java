package dev.bisz.world.wilderness;

import java.util.Objects;
import org.bukkit.plugin.Plugin;

/** Selects the best available chunk regenerator without hard-failing. */
public final class Regenerators {

  private Regenerators() {}

  /**
   * Returns a WorldEdit regenerator when WorldEdit is installed, otherwise a
   * safe no-op regenerator.
   */
  public static ChunkRegenerator create(Plugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null) {
      plugin
        .getLogger()
        .warning(
          "WorldEdit is not installed; wilderness regeneration is disabled."
        );
      return new NoopChunkRegenerator();
    }
    try {
      ChunkRegenerator regenerator = new WorldEditChunkRegenerator();
      plugin
        .getLogger()
        .info("Wilderness regeneration: " + regenerator.name() + ".");
      return regenerator;
    } catch (LinkageError error) {
      plugin
        .getLogger()
        .warning(
          "WorldEdit API is incompatible; wilderness regeneration is disabled: " +
          error.getMessage()
        );
      return new NoopChunkRegenerator();
    }
  }
}
