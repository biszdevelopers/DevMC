package dev.bisz.menus;

/** Controls which direction items may move through a mapped storage cell. */
public enum StorageAccess {
  /** Items are displayed but cannot move. */
  VIEW_ONLY,
  /** Items may leave storage, but player items may not enter it. */
  TAKE_ONLY,
  /** Items may move in either direction. */
  READ_WRITE,
}
