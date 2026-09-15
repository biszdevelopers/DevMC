package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class ItemRuntimeListenerTest {

  @Test
  void rejectsShiftClickingAnEmptySlotWhileCarryingAnItem() {
    assertTrue(ItemRuntimeListener.isInvalidShiftPlacement(
      true,
      null,
      new ItemStack(Material.DIAMOND)
    ));
    assertTrue(ItemRuntimeListener.isInvalidShiftPlacement(
      true,
      new ItemStack(Material.AIR),
      new ItemStack(Material.DIAMOND)
    ));
  }

  @Test
  void allowsNormalInventoryClicks() {
    ItemStack item = new ItemStack(Material.DIAMOND);

    assertFalse(ItemRuntimeListener.isInvalidShiftPlacement(false, null, item));
    assertFalse(ItemRuntimeListener.isInvalidShiftPlacement(true, item, item));
    assertFalse(ItemRuntimeListener.isInvalidShiftPlacement(true, null, null));
  }
}
