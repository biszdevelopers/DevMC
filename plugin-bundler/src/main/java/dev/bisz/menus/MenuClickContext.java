package dev.bisz.menus;

import java.util.Optional;
import java.util.OptionalInt;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Information passed to a menu item click callback. */
public final class MenuClickContext {

  private final Player player;
  private final MenuSession session;
  private final int slot;
  private final Integer storageSlot;
  private final StorageProvider storageProvider;
  private final InventoryClickEvent event;

  MenuClickContext(
    Player player,
    MenuSession session,
    int slot,
    Integer storageSlot,
    StorageProvider storageProvider,
    InventoryClickEvent event
  ) {
    this.player = player;
    this.session = session;
    this.slot = slot;
    this.storageSlot = storageSlot;
    this.storageProvider = storageProvider;
    this.event = event;
  }

  /** Returns the player viewing the menu. */
  public Player player() {
    return player;
  }

  /** Returns the active menu session. */
  public MenuSession session() {
    return session;
  }

  /** Returns the raw slot in the top menu inventory. */
  public int slot() {
    return slot;
  }

  /** Returns the mapped storage-provider index, when this is a storage cell. */
  public OptionalInt storageSlot() {
    return storageSlot == null
      ? OptionalInt.empty()
      : OptionalInt.of(storageSlot);
  }

  /** Returns the mapped storage-provider index, when this is a storage cell. */
  public OptionalInt storageIndex() {
    return storageSlot();
  }

  /** Returns the storage provider when this click belongs to a mapped storage cell. */
  public Optional<StorageProvider> storageProvider() {
    return Optional.ofNullable(storageProvider);
  }

  /** Returns the already-cancelled Bukkit event for inspection. */
  public InventoryClickEvent event() {
    return event;
  }
}
