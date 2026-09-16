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
      item(Material.DIAMOND, 1)
    ));
    assertTrue(MenuManager.isInvalidShiftPlacement(
      true,
      item(Material.AIR, 1),
      item(Material.DIAMOND, 1)
    ));
  }

  @Test
  void allowsNormalMenuClicks() {
    ItemStack item = item(Material.DIAMOND, 1);

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
    newItems.put(0, item(Material.DIAMOND, 3));
    newItems.put(1, item(Material.DIAMOND, 1));
    newItems.put(2, item(Material.DIAMOND, 1));
    newItems.put(3, item(Material.DIAMOND, 1));
    newItems.put(9, item(Material.DIAMOND, 5));

    Map<StorageSlot, Integer> deltas = MenuManager.storageDragDeltas(
      newItems,
      mappings,
      9,
      mapping ->
        mapping == writable ? item(Material.DIAMOND, 1) : null
    );

    assertEquals(Map.of(writable, 2), deltas);
  }

  /**
   * Paper 26.2 ItemStack constructors require a live server RegistryAccess.
   * These helpers only exercise type/amount calculations, so keep the unit
   * test independent from a bootstrapped Paper server.
   */
  private static ItemStack item(Material type, int amount) {
    return new TestItemStack(type, amount);
  }

  private static final class TestItemStack extends ItemStack {
    private final Material type;
    private final int amount;

    private TestItemStack(Material type, int amount) {
      super();
      this.type = type;
      this.amount = amount;
    }

    @Override
    public Material getType() {
      return type;
    }

    @Override
    public int getAmount() {
      return amount;
    }
  }
}
