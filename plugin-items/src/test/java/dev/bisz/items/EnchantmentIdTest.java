package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class EnchantmentIdTest {
  @Test void normalizesAndRoundTripsNamespacedIds() {
    EnchantmentId id = EnchantmentId.parse("Demo:Path/Test");
    assertEquals("demo", id.namespace());
    assertEquals("path/test", id.path());
    assertEquals("demo:path/test", id.toString());
  }

  @Test void rejectsMalformedIds() {
    assertThrows(IllegalArgumentException.class, () -> EnchantmentId.parse("sharpness"));
    assertThrows(IllegalArgumentException.class, () -> EnchantmentId.parse("minecraft:"));
    assertThrows(IllegalArgumentException.class, () -> EnchantmentId.parse("bad:id:again"));
  }
}
