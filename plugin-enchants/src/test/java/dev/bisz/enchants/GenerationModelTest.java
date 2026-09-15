package dev.bisz.enchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.bisz.items.CustomEnchantment;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.EnchantmentProperties;
import dev.bisz.enchants.items.ImpactResistanceEnchantment;
import dev.bisz.enchants.items.LethalityEnchantment;
import dev.bisz.enchants.items.WingedEnchantment;
import java.util.Map;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GenerationModelTest {
  @BeforeAll static void loadCatalog() {
    TestConfig.bootstrap();
  }

  @Test void bookshelfStatisticsRemainUncappedForDisplay() {
    assertEquals(500, new BookshelfModifierData(50, 500).extraChancePercent());
  }

  @Test void bookshelfRollsUseIndependentChancesAndCapAtFourExtras() {
    assertEquals(0, DefaultEnchantmentGenerator.extraRolls(0, fixed(0)));
    assertEquals(1, DefaultEnchantmentGenerator.extraRolls(99, fixed(0)));
    assertEquals(0, DefaultEnchantmentGenerator.extraRolls(99, fixed(99)));
    assertEquals(1, DefaultEnchantmentGenerator.extraRolls(100, fixed(99)));
    assertEquals(4, DefaultEnchantmentGenerator.extraRolls(400, fixed(0)));
    assertEquals(4, DefaultEnchantmentGenerator.extraRolls(1200, fixed(0)));
  }

  @Test void offerCostsAreConstantForTheMaterial() {
    assertEquals(new EnchantingCosts.OfferCost(0, 1), EnchantingCosts.roll(0, fixed(0)));
    assertEquals(new EnchantingCosts.OfferCost(25, 1), EnchantingCosts.roll(25, fixed(10)));
    assertEquals(new EnchantingCosts.OfferCost(25, 1), EnchantingCosts.roll(99, fixed(10)));
  }

  @Test void copiedYamlMaximumLevelsCoverCustomAndVanillaEnchantments() {
    assertEquals(5, EnchantmentCatalog.maximumLevel(new LethalityEnchantment()));
    assertEquals(4, EnchantmentCatalog.maximumLevel(new ImpactResistanceEnchantment()));
    assertEquals(1, EnchantmentCatalog.maximumLevel(new WingedEnchantment()));
  }

  @Test void offersAreOrderedByExperienceThenLapis() {
    TestEnchantment enchantment = new TestEnchantment();
    List<EnchantmentOffer> ordered = DefaultEnchantmentGenerator.orderOffers(List.of(
      new EnchantmentOffer(4, 2, Map.of(enchantment, new EnchantmentData(1)), Map.of()),
      new EnchantmentOffer(2, 9, Map.of(enchantment, new EnchantmentData(1)), Map.of()),
      new EnchantmentOffer(2, 3, Map.of(enchantment, new EnchantmentData(1)), Map.of())
    ));
    assertEquals(List.of(3, 9, 2), ordered.stream().map(EnchantmentOffer::lapisLazuli).toList());
  }

  @Test void offersValidateCostsAndDefensivelyCopyData() {
    TestEnchantment enchantment = new TestEnchantment();
    EnchantmentOffer offer = new EnchantmentOffer(5, 10, Map.of(enchantment, new EnchantmentData(1)), Map.of());
    assertEquals(5, offer.experienceLevels());
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentOffer(0, 1, offer.baseEnchantments(), Map.of()));
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentOffer(1, 1, Map.of(), Map.of()));
  }

  private static Random fixed(int value) {
    return new Random() { @Override public int nextInt(int bound) { return Math.min(value, bound - 1); } };
  }

  private static final class TestEnchantment extends CustomEnchantment {
    private TestEnchantment() { super(EnchantmentId.of("test", "sample"), EnchantmentProperties.builder().build()); }
  }
}
