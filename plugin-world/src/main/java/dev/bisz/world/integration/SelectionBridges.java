package dev.bisz.world.integration;

import java.util.Objects;
import org.bukkit.plugin.Plugin;

/** Selects a WorldEdit selection bridge without hard-failing when absent. */
public final class SelectionBridges {

  private SelectionBridges() {}

  /** Returns a WorldEdit selection bridge when available, else a disabled one. */
  public static SelectionBridge create(Plugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null) {
      return new DisabledSelectionBridge();
    }
    try {
      SelectionBridge bridge = new WorldEditSelectionBridge();
      plugin
        .getLogger()
        .info("Settlement selection backend: " + bridge.name() + ".");
      return bridge;
    } catch (LinkageError error) {
      plugin
        .getLogger()
        .warning(
          "WorldEdit API is incompatible; settlement selection is disabled: " +
          error.getMessage()
        );
      return new DisabledSelectionBridge();
    }
  }
}
