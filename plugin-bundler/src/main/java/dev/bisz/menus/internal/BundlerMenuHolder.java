/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.inventory.Inventory
 */
package dev.bisz.menus.internal;

import dev.bisz.menus.MenuInventoryHolder;
import java.util.UUID;
import org.bukkit.inventory.Inventory;

public final class BundlerMenuHolder implements MenuInventoryHolder {

  private final UUID sessionId;

  public BundlerMenuHolder(UUID sessionId) {
    this.sessionId = sessionId;
  }

  public UUID sessionId() {
    return this.sessionId;
  }

  public Inventory getInventory() {
    return null;
  }
}
