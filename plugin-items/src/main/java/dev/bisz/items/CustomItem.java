/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.items;

import dev.bisz.items.DevItem;
import dev.bisz.items.ItemId;
import dev.bisz.items.ItemProperties;

public abstract class CustomItem extends DevItem {

  protected CustomItem(ItemId id, ItemProperties properties) {
    super(id, properties);
  }

  @Override
  public final boolean vanilla() {
    return false;
  }
}
