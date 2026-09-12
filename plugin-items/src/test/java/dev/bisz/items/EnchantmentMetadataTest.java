package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class EnchantmentMetadataTest {
  private static final NamespacedKey EXTRA = key("enchants:extra_roll");
  private static final NamespacedKey COUNT = key("test:count");

  @Test void supportsOnlyBooleanAndIntegerValues() {
    assertEquals(true, new EnchantmentData(1, Map.of(EXTRA, true)).metadata().get(EXTRA));
    assertEquals(4, new EnchantmentData(1, Map.of(COUNT, 4)).metadata().get(COUNT));
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentData(1, Map.of(COUNT, "four")));
  }

  @Test void codecRoundTripsKnownAndUnknownIdsAndKeys() {
    Map<EnchantmentId, Map<NamespacedKey, Object>> values = new LinkedHashMap<>();
    values.put(EnchantmentId.of("minecraft", "sharpness"), Map.of(EXTRA, true, COUNT, 7));
    values.put(EnchantmentId.of("future", "unknown"), Map.of(key("future:flag"), false));
    String encoded = DevItemStack.encodeEnchantmentMetadata(values);
    assertEquals(values, DevItemStack.decodeEnchantmentMetadata(encoded));
    assertEquals(Map.of(), DevItemStack.decodeEnchantmentMetadata(null));
  }

  @Test void anvilUsesIntersectionBooleanAndAndIntegerMinimum() {
    NamespacedKey missing = key("test:missing");
    Map<NamespacedKey, Object> merged = DevItemStack.mergeMetadataValues(
      Map.of(EXTRA, true, COUNT, 9, missing, true),
      Map.of(EXTRA, false, COUNT, 4)
    );
    assertEquals(false, merged.get(EXTRA));
    assertEquals(4, merged.get(COUNT));
    assertFalse(merged.containsKey(missing));
  }

  @Test void extraRollRequiresBooleanTrue() {
    assertTrue(EnchantmentDisplay.extraRoll(Map.of(EXTRA, true)));
    assertFalse(EnchantmentDisplay.extraRoll(Map.of(EXTRA, false)));
    assertFalse(EnchantmentDisplay.extraRoll(Map.of(COUNT, 1)));
  }

  @Test void metadataChangesRenderingButNotItemLibOrdering() {
    TestEnchantment commonHigh = new TestEnchantment("common_high", "Zulu", Quality.COMMON);
    TestEnchantment epicLow = new TestEnchantment("epic_low", "Alpha", Quality.EPIC);
    TestEnchantment commonExtra = new TestEnchantment("common_extra", "Beta", Quality.COMMON);
    Map<DevEnchantment, EnchantmentData> values = new LinkedHashMap<>();
    values.put(commonHigh, new EnchantmentData(5));
    values.put(epicLow, new EnchantmentData(1));
    values.put(commonExtra, new EnchantmentData(2, Map.of(EXTRA, true)));
    List<Map.Entry<DevEnchantment, EnchantmentData>> ordered = EnchantmentDisplay.order(values, null);
    assertEquals(List.of(epicLow, commonHigh, commonExtra), ordered.stream().map(Map.Entry::getKey).toList());
    assertEquals("§e✎ Beta II", EnchantmentDisplay.renderLine(commonExtra, values.get(commonExtra), null));
  }

  private static NamespacedKey key(String value) {
    return Objects.requireNonNull(NamespacedKey.fromString(value));
  }

  private static final class TestEnchantment extends CustomEnchantment {
    private final String name;
    private TestEnchantment(String id, String name, Quality quality) {
      super(EnchantmentId.of("test", id), EnchantmentProperties.builder().quality(quality).build());
      this.name = name;
    }
    @Override protected String renderName(Player viewer) { return name; }
  }
}
