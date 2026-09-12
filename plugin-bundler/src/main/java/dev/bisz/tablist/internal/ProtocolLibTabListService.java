/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 */
package dev.bisz.tablist.internal;

import dev.bisz.tablist.TabListService;
import dev.bisz.tablist.internal.ProtocolLibPacketBridge;
import org.bukkit.entity.Player;

public final class ProtocolLibTabListService implements TabListService {

  private final ProtocolLibPacketBridge bridge = new ProtocolLibPacketBridge();

  @Override
  public void set(Player player, String header, String footer) {
    this.bridge.setTabList(player, header, footer);
  }
}
