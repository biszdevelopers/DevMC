/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package dev.bisz.items;

import dev.bisz.items.DevItem;
import dev.bisz.items.ItemId;
import dev.bisz.items.ItemProperties;
import org.bukkit.Material;

public final class UnresolvedItem extends DevItem {

  public UnresolvedItem(ItemId id, Material material) {
    super(id, ItemProperties.builder(material).build());
  }

  @Override
  public boolean vanilla() {
    return false;
  }
}
