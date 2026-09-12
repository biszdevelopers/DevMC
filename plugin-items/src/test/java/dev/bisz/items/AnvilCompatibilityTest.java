package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class AnvilCompatibilityTest {
  @Test void swordDamageConflictsDoNotDenyTheMerge() {
    assertFalse(ItemRuntimeListener.denyBowMerge(
      Material.DIAMOND_SWORD,
      Map.of(enchantment("sharpness"), 1),
      Map.of(enchantment("smite"), 1, enchantment("bane_of_arthropods"), 1)
    ));
  }

  @Test void bowsRejectMendingAndInfinityAsAWholeMerge() {
    DevEnchantment mending = enchantment("mending");
    DevEnchantment infinity = enchantment("infinity");
    assertTrue(ItemRuntimeListener.denyBowMerge(
      Material.BOW, Map.of(mending, 1), Map.of(infinity, 1)
    ));
    assertTrue(ItemRuntimeListener.denyBowMerge(
      Material.BOW, Map.of(), Map.of(mending, 1, infinity, 1)
    ));
    assertFalse(ItemRuntimeListener.denyBowMerge(
      Material.BOW, Map.of(mending, 1), Map.of()
    ));
    assertFalse(ItemRuntimeListener.denyBowMerge(
      Material.CROSSBOW, Map.of(mending, 1), Map.of(infinity, 1)
    ));
  }

  private static DevEnchantment enchantment(String path) {
    return new CustomEnchantment(
      EnchantmentId.of("minecraft", path),
      EnchantmentProperties.builder().build()
    ) {};
  }
}
