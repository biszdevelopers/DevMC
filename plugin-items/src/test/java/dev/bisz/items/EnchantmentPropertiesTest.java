package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class EnchantmentPropertiesTest {
  @Test void defaultsAreCommonAndNonTicking() {
    EnchantmentProperties properties = EnchantmentProperties.builder().build();
    assertEquals(Quality.COMMON, properties.quality());
    assertFalse(properties.handTicking());
    assertFalse(properties.inventoryTicking());
  }

  @Test void tickModesAreIndependent() {
    EnchantmentProperties properties = EnchantmentProperties.builder().quality(Quality.EPIC).handTicking(true).inventoryTicking(true).build();
    assertEquals(Quality.EPIC, properties.quality());
    assertTrue(properties.handTicking());
    assertTrue(properties.inventoryTicking());
  }
}
