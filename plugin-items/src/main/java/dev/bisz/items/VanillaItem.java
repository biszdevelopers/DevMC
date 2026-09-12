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

public final class VanillaItem extends DevItem {

  public VanillaItem(Material material) {
    super(
      ItemId.of("minecraft", material.getKey().getKey()),
      ItemProperties.builder(material)
        .quality(material == Material.ENCHANTED_BOOK ? Quality.RARE : Quality.COMMON)
        .build()
    );
  }

  @Override
  public boolean vanilla() {
    return true;
  }
}
