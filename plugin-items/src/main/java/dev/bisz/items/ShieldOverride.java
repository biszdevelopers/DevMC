package dev.bisz.items;

import org.bukkit.Material;

/** Makes shields participate in persistent durability handling without sockets. */
final class ShieldOverride extends OverrideDamageableVanillaItem {
  ShieldOverride() { super(Material.SHIELD, ItemProperties.builder(Material.SHIELD).build()); }
}
