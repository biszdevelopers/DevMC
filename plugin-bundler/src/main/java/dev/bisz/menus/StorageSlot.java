package dev.bisz.menus;

/** Immutable mapping between a menu cell and a logical storage-provider index. */
public final class StorageSlot {

  private final int menuSlot;
  private final int storageSlot;
  private final StorageAccess access;

  StorageSlot(int menuSlot, int storageSlot, StorageAccess access) {
    this.menuSlot = menuSlot;
    this.storageSlot = storageSlot;
    this.access = access;
  }

  StorageSlot(int menuSlot, int storageSlot, boolean readOnly) {
    this(
      menuSlot,
      storageSlot,
      readOnly ? StorageAccess.VIEW_ONLY : StorageAccess.READ_WRITE
    );
  }

  /** Returns the visible menu cell. */
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

  /** Returns whether all writes through the visible cell are rejected. */
  public boolean readOnly() {
    return access == StorageAccess.VIEW_ONLY;
  }

  /** Returns the allowed transfer direction. */
  public StorageAccess access() {
    return access;
  }
}
