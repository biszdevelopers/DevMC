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
      item(Material.DIAMOND, 1)
    ));
    assertTrue(ItemRuntimeListener.isInvalidShiftPlacement(
      true,
      item(Material.AIR, 1),
      item(Material.DIAMOND, 1)
    ));
  }

  @Test
  void allowsNormalInventoryClicks() {
    ItemStack item = item(Material.DIAMOND, 1);

    assertFalse(ItemRuntimeListener.isInvalidShiftPlacement(false, null, item));
    assertFalse(ItemRuntimeListener.isInvalidShiftPlacement(true, item, item));
    assertFalse(ItemRuntimeListener.isInvalidShiftPlacement(true, null, null));
  }

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
