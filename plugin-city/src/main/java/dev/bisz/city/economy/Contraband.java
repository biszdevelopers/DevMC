package dev.bisz.city.economy;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Marks and detects items the system vendor refuses to buy. */
public final class Contraband {

  private static final NamespacedKey KEY = new NamespacedKey(
    "city",
    "contraband"
  );

  private Contraband() {}

  /** Tags a stack as contraband in place. */
  public static void mark(ItemStack stack) {
    if (stack == null || stack.getType().isAir()) return;
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) return;
    meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
    stack.setItemMeta(meta);
  }

  /** Whether a stack carries the contraband tag. */
  public static boolean isContraband(ItemStack stack) {
    if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
      return false;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) return false;
    return meta
      .getPersistentDataContainer()
      .has(KEY, PersistentDataType.BYTE);
  }
}
