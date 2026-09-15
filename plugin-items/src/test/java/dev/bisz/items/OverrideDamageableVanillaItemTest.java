package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class OverrideDamageableVanillaItemTest {
  @Test void durabilityNumbersUseCommaGrouping() {
    assertEquals("1,000", OverrideDamageableVanillaItem.format(1_000));
    assertEquals("12,345", OverrideDamageableVanillaItem.format(12_345));
  }
}
