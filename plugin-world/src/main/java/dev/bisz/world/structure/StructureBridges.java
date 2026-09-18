package dev.bisz.world.structure;

import java.util.Objects;
import org.bukkit.plugin.Plugin;

/** Selects a structure bridge without hard-failing on a missing WorldEdit. */
public final class StructureBridges {

  private StructureBridges() {}

  /** Returns a WorldEdit bridge when available, otherwise a disabled one. */
  public static StructureBridge create(Plugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null) {
      plugin
        .getLogger()
        .warning(
          "WorldEdit is not installed; structure pasting is disabled."
        );
      return new DisabledStructureBridge();
    }
    try {
      StructureBridge bridge = new WorldEditStructureBridge();
      plugin.getLogger().info("Structure backend: " + bridge.name() + ".");
      return bridge;
    } catch (LinkageError error) {
      plugin
        .getLogger()
        .warning(
          "WorldEdit API is incompatible; structure pasting is disabled: " +
          error.getMessage()
        );
      return new DisabledStructureBridge();
    }
  }
}
