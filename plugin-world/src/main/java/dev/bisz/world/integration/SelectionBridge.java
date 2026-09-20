package dev.bisz.world.integration;

import org.bukkit.entity.Player;

/**
 * Isolates the optional WorldEdit dependency so settlement zoning can use the
 * admin's current selection.
 */
public interface SelectionBridge {
  /** Whether a WorldEdit selection backend is available. */
  boolean available();

  /** Backend name for diagnostics. */
  String name();

  /** The player's current selection, or null when there is none. */
  SelectionRegion selection(Player player);

  /** The player's current selection in block coordinates, or null. */
  BlockRegion blockSelection(Player player);
}
