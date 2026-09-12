package dev.bisz.menus;

import java.util.Map;

final class RenderedMenuPage {

  private final int page;
  private final int pageCount;
  private final Map<Integer, MenuItem> items;
  private final Map<Integer, StorageSlot> storageSlots;
  private final Integer previousSlot;
  private final Integer nextSlot;

  RenderedMenuPage(
    int page,
    int pageCount,
    Map<Integer, MenuItem> items,
    Map<Integer, StorageSlot> storageSlots,
    Integer previousSlot,
    Integer nextSlot
  ) {
    this.page = page;
    this.pageCount = pageCount;
    this.items = Map.copyOf(items);
    this.storageSlots = Map.copyOf(storageSlots);
    this.previousSlot = previousSlot;
    this.nextSlot = nextSlot;
  }

  int page() {
    return page;
  }

  int pageCount() {
    return pageCount;
  }

  Map<Integer, MenuItem> items() {
    return items;
  }

  Map<Integer, StorageSlot> storageSlots() {
    return storageSlots;
  }

  Integer previousSlot() {
    return previousSlot;
  }

  Integer nextSlot() {
    return nextSlot;
  }
}
