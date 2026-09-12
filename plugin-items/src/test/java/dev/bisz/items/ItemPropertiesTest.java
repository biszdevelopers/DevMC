package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class ItemPropertiesTest {

  @Test
  void attackTriggerIsOptIn() {
    assertFalse(
      ItemProperties.builder(Material.WOODEN_SWORD).build().attackTriggering()
    );
    assertTrue(
      ItemProperties.builder(Material.WOODEN_SWORD)
        .attackTriggering(true)
        .build()
        .attackTriggering()
    );
  }
}
