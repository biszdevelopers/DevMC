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

public class VanillaItem extends DevItem {

  public VanillaItem(Material material) {
    this(
      material,
      ItemProperties.builder(material)
        .quality(material == Material.ENCHANTED_BOOK ? Quality.RARE : Quality.COMMON)
        .build()
    );
  }

  protected VanillaItem(Material material, ItemProperties properties) {
    super(ItemId.of("minecraft", material.getKey().getKey()), properties);
    if (properties.material() != material) {
      throw new IllegalArgumentException(
        "Vanilla item properties must use " + material
      );
    }
  }

  @Override
  public final boolean vanilla() {
    return true;
  }
}
