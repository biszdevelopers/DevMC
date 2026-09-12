package dev.bisz.menus;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Describes a completed write to a mapped storage-provider index. */
public final class MenuStorageChangeContext {

  private final Player player;
  private final MenuSession session;
  private final int menuSlot;
  private final int storageSlot;
  private final StorageProvider storageProvider;
  private final ItemStack previousItem;
  private final ItemStack item;

  MenuStorageChangeContext(
    Player player,
    MenuSession session,
    int menuSlot,
    int storageSlot,
    StorageProvider storageProvider,
    ItemStack previousItem,
    ItemStack item
  ) {
    this.player = player;
    this.session = session;
    this.menuSlot = menuSlot;
    this.storageSlot = storageSlot;
    this.storageProvider = storageProvider;
    this.previousItem = copy(previousItem);
    this.item = copy(item);
  }

  /** Returns the player who performed the storage action. */
  public Player player() {
    return player;
  }

  /** Returns the active menu session. */
  public MenuSession session() {
    return session;
  }

  /** Returns the cell in the visible menu. */
  public int menuSlot() {
    return menuSlot;
  }

  /** Returns the exact storage-provider index. */
  public int storageSlot() {
    return storageSlot;
  }

  /** Returns the exact storage-provider index. */
  public int storageIndex() {
    return storageSlot;
  }

  /** Returns the provider that accepted the completed write. */
  public StorageProvider storageProvider() {
    return storageProvider;
  }

  /** Returns a defensive copy of the value before the write, or {@code null}. */
  public ItemStack previousItem() {
    return copy(previousItem);
  }

  /** Returns a defensive copy of the value after the write, or {@code null}. */
  public ItemStack item() {
    return copy(item);
  }

  private static ItemStack copy(ItemStack value) {
    return value == null ? null : value.clone();
  }
}
