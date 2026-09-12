package dev.bisz.items;

import org.bukkit.Material;

/**
 * A class-based replacement for one generated vanilla item definition.
 *
 * <p>The stack remains a native Minecraft item while gaining DevItem metadata,
 * rendering, lifecycle, tick, consumption, and attack hooks.</p>
 */
public abstract class OverrideVanillaItem extends VanillaItem {

  protected OverrideVanillaItem(Material material, ItemProperties properties) {
    super(material, properties);
  }
}
