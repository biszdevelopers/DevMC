package dev.bisz.menus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
