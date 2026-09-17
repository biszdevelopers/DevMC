package dev.bisz.enchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class GenerationModelTest {
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

  @Test void maximumLevelsCoverCustomAndVanillaEnchantments() {
    assertEquals(5, EnchantmentCatalog.maximumLevel(new LethalityEnchantment()));
    assertEquals(4, EnchantmentCatalog.maximumLevel(new ImpactResistanceEnchantment()));
    assertEquals(1, EnchantmentCatalog.maximumLevel(new WingedEnchantment()));
  }

  @Test void socketCandidatesOnlyKeepEnchantmentsThatCanStillBeApplied() {
    EnchantmentSlot fatality = EnchantmentSlot.typed(0, EnchantmentCategory.FATALITY);
    var lethality = new LethalityEnchantment();
    var winged = new WingedEnchantment();

    assertEquals(
      List.of(lethality),
      EnchantmentCatalog.candidates(fatality, Material.IRON_SWORD, List.of(), List.of(lethality, winged))
    );
    assertTrue(EnchantmentCatalog.candidates(
      fatality, Material.IRON_SWORD, List.of(lethality), List.of(lethality, winged)
    ).isEmpty(), "A present non-repeatable enchantment and a wrong-category enchantment leave no candidate");
  }

  @Test void socketCandidatesAllowRepeatsButNeverConflicts() {
    EnchantmentSlot harvesting = EnchantmentSlot.typed(0, EnchantmentCategory.HARVESTING);
    var efficiency = new CatalogEnchantment("minecraft", "efficiency");
    var fortune = new CatalogEnchantment("minecraft", "fortune");
    var silkTouch = new CatalogEnchantment("minecraft", "silk_touch");

    assertEquals(
      List.of(efficiency),
      EnchantmentCatalog.candidates(
        harvesting, Material.DIAMOND_PICKAXE, List.of(efficiency, silkTouch),
        List.of(efficiency, fortune, silkTouch))
    );
    assertEquals(
      List.of(efficiency),
      EnchantmentCatalog.candidates(
        harvesting, Material.DIAMOND_PICKAXE, List.of(silkTouch),
        List.of(fortune, efficiency))
    );
  }

  @Test void socketCandidatesAreEmptyOnlyWhenEveryRuleBlocksEveryEnchantment() {
    EnchantmentSlot sustainability = EnchantmentSlot.typed(0, EnchantmentCategory.SUSTAINABILITY);
    var mending = new CatalogEnchantment("minecraft", "mending");
    var unbreaking = new CatalogEnchantment("minecraft", "unbreaking");

    assertTrue(EnchantmentCatalog.candidates(
      sustainability, Material.DIAMOND_PICKAXE, List.of(mending, unbreaking), List.of(mending, unbreaking)
    ).isEmpty(), "Two mendings style saturation is the only no-offer case");
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
    EnchantmentOffer free = new EnchantmentOffer(0, 0, offer.baseEnchantments(), Map.of());
    assertEquals(0, free.experienceLevels());
    assertEquals(0, free.lapisLazuli());
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentOffer(-1, 1, offer.baseEnchantments(), Map.of()));
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentOffer(1, -1, offer.baseEnchantments(), Map.of()));
    assertThrows(IllegalArgumentException.class, () -> new EnchantmentOffer(1, 1, Map.of(), Map.of()));
  }

  private static Random fixed(int value) {
    return new Random() { @Override public int nextInt(int bound) { return Math.min(value, bound - 1); } };
  }

  private static final class TestEnchantment extends CustomEnchantment {
    private TestEnchantment() { super(EnchantmentId.of("test", "sample"), EnchantmentProperties.builder().build()); }
  }

  private static final class CatalogEnchantment extends CustomEnchantment {
    private CatalogEnchantment(String namespace, String path) {
      super(EnchantmentId.of(namespace, path), EnchantmentProperties.builder().build());
    }
  }
}
