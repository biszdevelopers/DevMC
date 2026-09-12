/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 */
package dev.bisz.tablist.internal;

import org.bukkit.entity.Player;

final class ProtocolLibPacketBridge {

  ProtocolLibPacketBridge() {}

  void setTabList(Player player, String header, String footer) {
    if (!this.isAvailable()) {
      throw new IllegalStateException("ProtocolLib is unavailable");
    }
    player.setPlayerListHeaderFooter(header, footer);
  }

  private boolean isAvailable() {
    try {
      Class.forName(
        "com.comphenix.protocol.ProtocolLibrary",
        false,
        this.getClass().getClassLoader()
      );
      return true;
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }
}
