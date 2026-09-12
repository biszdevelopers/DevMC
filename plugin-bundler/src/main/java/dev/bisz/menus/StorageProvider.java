package dev.bisz.menus;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Scalable indexed storage used by menu storage mappings.
 *
 * <p>A provider is a logical collection rather than a GUI inventory, so its size is
 * not limited to 54. Implementations are called on the Bukkit server thread and must
 * return promptly; database-backed providers should normally read from a cache or a
 * previously loaded snapshot. Returned and supplied stacks are defensively cloned by
 * the menu runtime.</p>
 *
 * <p>Read-only providers only need to implement {@link #getItem(int)}. Providers
 * with a known finite capacity should also override {@link #size()}. Writable
 * providers override {@link #setItem(int,
 * ItemStack)} and should complete the write synchronously or throw before changing
 * external state.</p>
 */
public abstract class StorageProvider {

  /**
   * Returns the number of addressable logical indices. The default represents an
   * effectively unbounded provider; finite providers should override it.
   */
  public int size() {
    return Integer.MAX_VALUE;
  }

  /** Returns the item at an index, or {@code null} when that index is empty. */
  public abstract ItemStack getItem(int index);

  /** Returns an item rendered for a specific viewer when a provider supports it. */
  public ItemStack getItem(int index, Player viewer) {
    return getItem(index);
  }

  /**
   * Replaces the item at an index.
   *
   * @throws UnsupportedOperationException when this provider is read-only
   */
  public void setItem(int index, ItemStack item) {
    throw new UnsupportedOperationException(
      getClass().getSimpleName() + " is read-only"
    );
  }

  /** Returns whether the logical index currently exists. */
  public boolean containsIndex(int index) {
    return index >= 0 && index < size();
  }

  /** Wraps a real or temporary Bukkit inventory as a storage provider. */
  public static StorageProvider fromInventory(Inventory inventory) {
    return new BukkitInventoryStorageProvider(
      Objects.requireNonNull(inventory, "inventory")
    );
  }

  private static final class BukkitInventoryStorageProvider
    extends StorageProvider {

    private final Inventory inventory;

    private BukkitInventoryStorageProvider(Inventory inventory) {
      this.inventory = inventory;
    }

    @Override
    public int size() {
      return inventory.getSize();
    }

    @Override
    public ItemStack getItem(int index) {
      requireIndex(index);
      ItemStack item = inventory.getItem(index);
      return item == null ? null : item.clone();
    }

    @Override
    public void setItem(int index, ItemStack item) {
      requireIndex(index);
      inventory.setItem(index, item == null ? null : item.clone());
    }

    private void requireIndex(int index) {
      if (!containsIndex(index)) {
        throw new IndexOutOfBoundsException(
          "Bukkit inventory has no index " + index
        );
      }
    }
  }
}
