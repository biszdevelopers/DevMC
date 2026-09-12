/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.NamespacedKey
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package dev.bisz.nbt;

import dev.bisz.nbt.ItemDataService;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class PersistentItemDataService implements ItemDataService {

  private final Plugin plugin;

  public PersistentItemDataService(Plugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public Optional<String> getString(ItemStack item, String key) {
    ItemMeta meta = item.getItemMeta();
    return meta == null
      ? Optional.empty()
      : Optional.ofNullable(
        (String) meta
          .getPersistentDataContainer()
          .get(this.key(key), PersistentDataType.STRING)
      );
  }

  @Override
  public ItemStack setString(ItemStack item, String key, String value) {
    ItemStack copy = item.clone();
    ItemMeta meta = copy.getItemMeta();
    if (meta == null) {
      throw new IllegalArgumentException("Item has no mutable meta");
    }
    meta
      .getPersistentDataContainer()
      .set(this.key(key), PersistentDataType.STRING, value);
    copy.setItemMeta(meta);
    return copy;
  }

  @Override
  public ItemStack remove(ItemStack item, String key) {
    ItemStack copy = item.clone();
    ItemMeta meta = copy.getItemMeta();
    if (meta != null) {
      meta.getPersistentDataContainer().remove(this.key(key));
      copy.setItemMeta(meta);
    }
    return copy;
  }

  private NamespacedKey key(String value) {
    if (!value.matches("[a-z0-9/._-]{1,128}")) {
      throw new IllegalArgumentException("Invalid PDC key");
    }
    return new NamespacedKey(this.plugin, value);
  }
}
