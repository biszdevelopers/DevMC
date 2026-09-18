package dev.bisz.world.integration;

import org.bukkit.entity.Player;

/** No-op selection backend used when WorldEdit is unavailable. */
public final class DisabledSelectionBridge implements SelectionBridge {

  @Override
  public boolean available() {
    return false;
  }

  @Override
  public String name() {
    return "disabled";
  }

  @Override
  public SelectionRegion selection(Player player) {
    return null;
  }

  @Override
  public BlockRegion blockSelection(Player player) {
    return null;
  }
}
