package dev.bisz.enchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.bisz.items.CustomEnchantment;
import dev.bisz.items.DevEnchantment;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.EnchantmentProperties;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GenerationModelTest {
  @Test void bookshelfStatisticsAreUncapped() {
    assertEquals(new BookshelfModifierData(35, 350), new BookshelfModifierData(35, 350));
  }

  @Test void percentageProducesGuaranteedAndRemainderRolls() {
    assertEquals(0, DefaultEnchantmentGenerator.extraRolls(0, fixed(0)));
    assertEquals(3, DefaultEnchantmentGenerator.extraRolls(300, fixed(0)));
    assertEquals(4, DefaultEnchantmentGenerator.extraRolls(350, fixed(49)));
    assertEquals(3, DefaultEnchantmentGenerator.extraRolls(350, fixed(50)));
  }

  @Test void bookshelfRollsAreCappedByPowerAndOfferTier() {
    assertEquals(1, DefaultEnchantmentGenerator.extraRollsForTier(500, 0, fixed(0)));
    assertEquals(2, DefaultEnchantmentGenerator.extraRollsForTier(500, 1, fixed(0)));
    assertEquals(5, DefaultEnchantmentGenerator.extraRollsForTier(500, 2, fixed(0)));
    assertEquals(5, DefaultEnchantmentGenerator.extraRollsForTier(1200, 2, fixed(0)));
    assertEquals(3, DefaultEnchantmentGenerator.extraRollsForTier(350, 2, fixed(50)));
    assertThrows(IllegalArgumentException.class, () -> DefaultEnchantmentGenerator.extraRollCap(3));
  }

  @Test void offersValidateCostsAndDefensivelyCopyData() {
    TestEnchantment enchantment = new TestEnchantment();
    EnchantmentOffer offer = new EnchantmentOffer(5, 10, Map.of(enchantment, new EnchantmentData(2)), Map.of());
    assertEquals(5, offer.experienceLevels());
    assertEquals(10, offer.lapisLazuli());
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentOffer(0, 1, offer.baseEnchantments(), Map.of()));
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentOffer(1, 1, Map.of(), Map.of()));
  }

  @Test void enchantmentCountsControlRoundedCostMultiplier() {
    TestEnchantment first = new TestEnchantment("first");
    TestEnchantment second = new TestEnchantment("second");
    TestEnchantment third = new TestEnchantment("third");
    double oneLevelOne = Math.log(4.134 + Math.E);
    double threeWithOneUpgrade = Math.log(4.134 * 3 + 2 + Math.E);
    assertEquals(oneLevelOne, DefaultEnchantmentGenerator.costMultiplier(
      Map.of(first, new EnchantmentData(1)), Map.of()
    ), 1.0e-12);
    assertEquals(threeWithOneUpgrade, DefaultEnchantmentGenerator.costMultiplier(
      Map.of(first, new EnchantmentData(2), second, new EnchantmentData(1)),
      Map.of(third, new EnchantmentData(1))
    ), 1.0e-12);
    assertEquals(Math.round(5 * threeWithOneUpgrade),
      DefaultEnchantmentGenerator.scaledCost(5, threeWithOneUpgrade));
  }

  @Test void offerRejectsInvalidCostMultipliers() {
    TestEnchantment enchantment = new TestEnchantment();
    Map<DevEnchantment, EnchantmentData> data = Map.of(enchantment, new EnchantmentData(1));
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentOffer(1, 1, 0, data, Map.of()));
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentOffer(1, 1, Double.NaN, data, Map.of()));
  }

  private static Random fixed(int value) {
    return new Random() {
      @Override public int nextInt(int bound) { return Math.min(value, bound - 1); }
    };
  }

  private static final class TestEnchantment extends CustomEnchantment {
    private TestEnchantment() {
      this("sample");
    }
    private TestEnchantment(String id) {
      super(EnchantmentId.of("test", id), EnchantmentProperties.builder().build());
    }
  }
}
