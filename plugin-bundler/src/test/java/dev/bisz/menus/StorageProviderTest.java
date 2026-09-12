package dev.bisz.menus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class StorageProviderTest {

  @Test
  void customProviderIsUnboundedByDefaultAndMayOnlyOverrideReads() {
    StorageProvider provider = new StorageProvider() {
      @Override
      public ItemStack getItem(int index) {
        return null;
      }
    };

    assertTrue(provider.containsIndex(10_000));
    assertThrows(UnsupportedOperationException.class, () ->
      provider.setItem(10_000, null)
    );
  }

  @Test
  void finiteProviderControlsItsLogicalCapacity() {
    StorageProvider provider = new StorageProvider() {
      @Override
      public int size() {
        return 2_500;
      }

      @Override
      public ItemStack getItem(int index) {
        return null;
      }
    };

    assertTrue(provider.containsIndex(2_499));
    assertFalse(provider.containsIndex(2_500));
    assertFalse(provider.containsIndex(-1));
  }
}
