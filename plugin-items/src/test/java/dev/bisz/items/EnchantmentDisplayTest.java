package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class EnchantmentDisplayTest {
  @Test void descriptionHookReceivesCompleteData() {
    TestEnchantment enchantment = new TestEnchantment("one");
    EnchantmentData data = new EnchantmentData(3, Map.of(NamespacedKey.fromString("test:value"), 7));
    assertEquals("level=3,value=7", enchantment.displayDescription(null, data));
  }

  @Test void descriptionsAndSeparatorsAreControlledByFiveEntryLimit() {
    Map<DevEnchantment, EnchantmentData> five = entries(5);
    var rendered = EnchantmentDisplay.renderEntries(five, null);
    assertTrue(rendered.stream().anyMatch(line -> line.startsWith("§7level=")));
    assertTrue(rendered.contains(""));
    var six = EnchantmentDisplay.renderEntries(entries(6), null);
    assertFalse(six.stream().anyMatch(line -> line.startsWith("§7level=")));
    assertEquals(6, six.size());
  }

  @Test void statisticalDescriptionNumbersUsePositiveAndNegativeColors() {
    assertEquals(
      "Adds §a+12.5%§7 damage, removes §c-3§7 Protection, hits §a2§7 targets for §a3.0§7s.",
      EnchantmentDisplay.highlightStatistics("Adds +12.5% damage, removes -3 Protection, hits 2 targets for 3.0s.")
    );
    assertEquals(
      "Values: §a1§7/§a1.5§7/§a2§7; range §a4§7-§a8§7; code §71 stays untouched.",
      EnchantmentDisplay.highlightStatistics("Values: 1/1.5/2; range 4-8; code §71 stays untouched.")
    );
  }

  private static Map<DevEnchantment, EnchantmentData> entries(int count) {
    LinkedHashMap<DevEnchantment, EnchantmentData> values = new LinkedHashMap<>();
    for (int index = 0; index < count; index++) values.put(new TestEnchantment("test_" + index), new EnchantmentData(1));
    return values;
  }

  private static final class TestEnchantment extends CustomEnchantment {
    TestEnchantment(String path) { super(EnchantmentId.of("test", path), EnchantmentProperties.builder().build()); }
    @Override protected String renderDescription(Player viewer, EnchantmentData data) {
      return "level=" + data.level() + ",value=" + data.metadata().values().stream().findFirst().orElse(0);
    }
  }
}
