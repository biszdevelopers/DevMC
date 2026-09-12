/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 */
package dev.bisz.items;

import dev.bisz.items.DevItemStack;
import dev.bisz.items.ItemId;
import dev.bisz.items.ItemRegistry;
import dev.bisz.items.MetadataPair;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ItemFactory {

  private final ItemRegistry registry;
  private final NamespacedKey suppressRenderingKey;

  public ItemFactory(ItemRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.suppressRenderingKey = new NamespacedKey(
      ItemsPlugin.instance(),
      "suppress-rendering"
    );
  }

  public DevItemStack create(ItemId id, int amount, MetadataPair... overrides) {
    DevItemStack stack = this.registry.create(id, amount);
    for (MetadataPair override : overrides) {
      stack.metadata(override.key(), override.value());
    }
    return stack;
  }

  public DevItemStack wrap(ItemStack stack) {
    return this.registry.resolve(stack);
  }

  public DevItemStack refresh(ItemStack stack, Player viewer) {
    if (isRenderingSuppressed(stack) || viewer == null) return wrap(stack);
    DevItemStack resolved = this.wrap(stack);
    resolved.render(viewer);
    return resolved;
  }

  public DevItemStack refreshIfLocaleChanged(ItemStack stack, Player viewer) {
    if (isRenderingSuppressed(stack) || viewer == null) return wrap(stack);
    DevItemStack resolved = this.wrap(stack);
    if (!resolved.isRenderedFor(viewer)) {
      resolved.render(viewer);
    }
    return resolved;
  }

  /** Marks a utility/control item as outside ItemLib's rendering pipeline. */
  public ItemStack suppressRendering(ItemStack stack) {
    ItemStack copy = Objects.requireNonNull(stack, "stack").clone();
    var meta = copy.getItemMeta();
    if (meta != null) {
      meta
        .getPersistentDataContainer()
        .set(suppressRenderingKey, PersistentDataType.BYTE, (byte) 1);
      copy.setItemMeta(meta);
    }
    return copy;
  }

  /** Returns whether a utility/control item opted out of ItemLib rendering. */
  public boolean isRenderingSuppressed(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    return stack
      .getItemMeta()
      .getPersistentDataContainer()
      .has(suppressRenderingKey, PersistentDataType.BYTE);
  }

  /** Re-renders every renderable item in an inventory for its viewer. */
  public void forceRenderInventory(Player player) {
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      ItemStack current = player.getInventory().getItem(slot);
      if (
        current == null ||
        current.getType().isAir() ||
        isRenderingSuppressed(current)
      ) continue;
      player
        .getInventory()
        .setItem(slot, refresh(current, player).bukkitStack());
    }
  }
}
