package dev.bisz.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class MenuManagerClickTest {

  @Test
  void rejectsShiftClickingAnEmptyMenuSlotWhileCarryingAnItem() {
    assertTrue(MenuManager.isInvalidShiftPlacement(
      true,
      null,
      new ItemStack(Material.DIAMOND)
    ));
    assertTrue(MenuManager.isInvalidShiftPlacement(
      true,
      new ItemStack(Material.AIR),
      new ItemStack(Material.DIAMOND)
    ));
  }

  @Test
  void allowsNormalMenuClicks() {
    ItemStack item = new ItemStack(Material.DIAMOND);

    assertFalse(MenuManager.isInvalidShiftPlacement(false, null, item));
    assertFalse(MenuManager.isInvalidShiftPlacement(true, item, item));
    assertFalse(MenuManager.isInvalidShiftPlacement(true, null, null));
  }

  @Test
  void dragDeltasOnlyCoverWritableStorageCells() {
    StorageSlot writable = new StorageSlot(0, 0, StorageAccess.READ_WRITE);
    StorageSlot readOnly = new StorageSlot(1, 1, StorageAccess.VIEW_ONLY);
    StorageSlot takeOnly = new StorageSlot(2, 2, StorageAccess.TAKE_ONLY);
    Map<Integer, StorageSlot> mappings = new LinkedHashMap<>();
    mappings.put(0, writable);
    mappings.put(1, readOnly);
    mappings.put(2, takeOnly);
    Map<Integer, ItemStack> newItems = new LinkedHashMap<>();
    newItems.put(0, new ItemStack(Material.DIAMOND, 3));
    newItems.put(1, new ItemStack(Material.DIAMOND, 1));
    newItems.put(2, new ItemStack(Material.DIAMOND, 1));
    newItems.put(3, new ItemStack(Material.DIAMOND, 1));
    newItems.put(9, new ItemStack(Material.DIAMOND, 5));

    Map<StorageSlot, Integer> deltas = MenuManager.storageDragDeltas(
      newItems,
      mappings,
      9,
      mapping ->
        mapping == writable ? new ItemStack(Material.DIAMOND, 1) : null
    );

    assertEquals(Map.of(writable, 2), deltas);
  }
}
