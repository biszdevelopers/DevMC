package dev.bisz.enchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bisz.items.AbilityUsageMethod;
import dev.bisz.items.AbilityCooldown;
import dev.bisz.items.CustomEnchantment;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.EnchantmentProperties;
import dev.bisz.items.Quality;
import dev.bisz.enchants.items.AcrobaticsEnchantment;
import dev.bisz.enchants.items.ImpactResistanceEnchantment;
import dev.bisz.enchants.items.InflameEnchantment;
import dev.bisz.enchants.items.KnockbackEnchantment;
import dev.bisz.enchants.items.LethalityEnchantment;
import dev.bisz.enchants.items.NimbleEnchantment;
import dev.bisz.enchants.items.PenetrationEnchantment;
import dev.bisz.enchants.items.WingedEnchantment;
import dev.bisz.enchants.items.SocketedItemOverrides;
import dev.bisz.enchants.items.SocketedBookItem;
import java.util.ArrayList;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.entity.Arrow;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

class TypedSocketModelTest {
  @Test void everyExperienceLevelCostsTheVanillaFiveToSixAmount() {
    assertEquals(17, LinearExperience.POINTS_PER_LEVEL);
    assertEquals(0, LinearExperience.totalPoints(0, 0F));
    assertEquals(17, LinearExperience.totalPoints(1, 0F));
    assertEquals(85, LinearExperience.totalPoints(5, 0F));
    assertEquals(102, LinearExperience.totalPoints(6, 0F));
    assertEquals(94, LinearExperience.totalPoints(5, 9F / 17F));
  }

  @Test void mendingLevelsRiseThroughTheConfiguredTierThresholds() {
    assertEquals(1, SocketedVanillaItem.mendingLevel(0));
    assertEquals(1, SocketedVanillaItem.mendingLevel(1_999));
    assertEquals(2, SocketedVanillaItem.mendingLevel(2_000));
    assertEquals(2, SocketedVanillaItem.mendingLevel(21_999));
    assertEquals(3, SocketedVanillaItem.mendingLevel(22_000));
    assertEquals(4, SocketedVanillaItem.mendingLevel(42_000));
    assertEquals(5, SocketedVanillaItem.mendingLevel(62_000));
    assertEquals(6, SocketedVanillaItem.mendingLevel(112_000));
    assertEquals(7, SocketedVanillaItem.mendingLevel(162_000));
    assertEquals(8, SocketedVanillaItem.mendingLevel(212_000));
    assertEquals(9, SocketedVanillaItem.mendingLevel(312_000));
    assertEquals(10, SocketedVanillaItem.mendingLevel(512_000));
    assertEquals(10, SocketedVanillaItem.mendingLevel(911_999));
    assertEquals(11, SocketedVanillaItem.mendingLevel(912_000));
    assertEquals(11, SocketedVanillaItem.mendingLevel(Integer.MAX_VALUE));
  }

  @Test void mendingMultiplierRisesByTenthsToDouble() {
    assertEquals(1.0D, SocketedVanillaItem.mendingMultiplier(1), 0.000_001D);
    assertEquals(1.1D, SocketedVanillaItem.mendingMultiplier(2), 0.000_001D);
    assertEquals(1.9D, SocketedVanillaItem.mendingMultiplier(10), 0.000_001D);
    assertEquals(2.0D, SocketedVanillaItem.mendingMultiplier(11), 0.000_001D);
    assertEquals(2.0D, SocketedVanillaItem.mendingMultiplier(50), 0.000_001D);
    assertEquals(2_000, SocketedVanillaItem.nextMendingThreshold(1));
    assertEquals(512_000, SocketedVanillaItem.nextMendingThreshold(9));
    assertEquals(912_000, SocketedVanillaItem.nextMendingThreshold(10));
    assertEquals(-1, SocketedVanillaItem.nextMendingThreshold(11));
  }

  @Test void mendCountDefaultsToZeroAndClampsNegatives() {
    var metadata = Map.<org.bukkit.NamespacedKey, Object>of(
      SocketedVanillaItem.mendingCountKey(), 1_234
    );
    assertEquals(0, SocketedVanillaItem.mendCount(new EnchantmentData(1)));
    assertEquals(0, SocketedVanillaItem.mendCount(new EnchantmentData(1,
      Map.of(SocketedVanillaItem.mendingCountKey(), -5))));
    assertEquals(1_234, SocketedVanillaItem.mendCount(new EnchantmentData(1, metadata)));
    assertEquals("1,234", String.format(java.util.Locale.US, "%,d",
      SocketedVanillaItem.mendCount(new EnchantmentData(1, metadata))));
  }

  @Test void mendRemainderDefaultsToZeroAndClampsToTenths() {
    assertEquals(0, SocketedVanillaItem.mendRemainder(new EnchantmentData(1)));
    assertEquals(9, SocketedVanillaItem.mendRemainder(new EnchantmentData(1,
      Map.of(SocketedVanillaItem.mendingRemainderKey(), 14))));
    assertEquals(0, SocketedVanillaItem.mendRemainder(new EnchantmentData(1,
      Map.of(SocketedVanillaItem.mendingRemainderKey(), -3))));
    assertEquals(4, SocketedVanillaItem.mendRemainder(new EnchantmentData(1,
      Map.of(SocketedVanillaItem.mendingRemainderKey(), 4))));
  }

  @Test void mendingRepairCarriesTheFractionOfTenthMultipliers() {
    var first = SocketedVanillaItem.scaleMendingRepair(2, 100, 2_000, 0);
    assertEquals(2, first.repaired());
    assertEquals(2, first.remainder());
    var second = SocketedVanillaItem.scaleMendingRepair(2, 100, 2_000, first.remainder());
    assertEquals(2, second.repaired());
    assertEquals(4, second.remainder());
    var fifth = SocketedVanillaItem.scaleMendingRepair(2, 100, 2_000, 8);
    assertEquals(3, fifth.repaired());
    assertEquals(0, fifth.remainder());
  }

  @Test void mendingRepairAddsToTheExactLongRunMultiplier() {
    for (int[] tier : new int[][] {{1, 10}, {2, 11}, {5, 14}, {11, 20}}) {
      int count = tier[0] == 1 ? 0 : SocketedVanillaItem.nextMendingThreshold(tier[0] - 1);
      int total = 0;
      int remainder = 0;
      for (int mend = 0; mend < 20; mend++) {
        var repair = SocketedVanillaItem.scaleMendingRepair(2, 100, count, remainder);
        total += repair.repaired();
        remainder = repair.remainder();
      }
      assertEquals(40 * tier[1], total * 10 + remainder, "tier " + tier[0]);
    }
  }

  @Test void mendingRepairScalesAndClampsToMissingDurability() {
    assertEquals(14, SocketedVanillaItem.scaleMendingRepair(10, 100, 100_000, 0).repaired());
    assertEquals(20, SocketedVanillaItem.scaleMendingRepair(10, 100, 1_000_000, 0).repaired());
    assertEquals(5, SocketedVanillaItem.scaleMendingRepair(10, 5, 1_000_000, 0).repaired());
    assertEquals(0, SocketedVanillaItem.scaleMendingRepair(10, 0, 1_000_000, 0).repaired());
    assertEquals(0, SocketedVanillaItem.scaleMendingRepair(0, 100, 1_000_000, 5).repaired());
    assertEquals(5, SocketedVanillaItem.scaleMendingRepair(0, 100, 1_000_000, 5).remainder());
  }

  @Test void firstRunMigrationPreservesVanillaExperiencePoints() {
    assertEquals(55, LinearExperience.vanillaTotalPoints(5, 0F));
    assertEquals(72, LinearExperience.vanillaTotalPoints(6, 0F));
    assertEquals(79, LinearExperience.vanillaTotalPoints(6, 7F / 19F));
    assertEquals(1507, LinearExperience.vanillaTotalPoints(31, 0F));
  }

  @Test void shortbowCopiesTippedArrowPotionPayload() {
    PotionData base = new PotionData(PotionType.POISON, false, true);
    PotionEffect custom = new PotionEffect(PotionEffectType.SLOW, 80, 1);
    Color color = Color.fromRGB(12, 34, 56);
    List<Object[]> calls = new ArrayList<>();
    PotionMeta potion = (PotionMeta) Proxy.newProxyInstance(
      PotionMeta.class.getClassLoader(), new Class<?>[] {PotionMeta.class}, (proxy, method, arguments) ->
        switch (method.getName()) {
          case "getBasePotionData" -> base;
          case "getCustomEffects" -> List.of(custom);
          case "hasColor" -> true;
          case "getColor" -> color;
          default -> defaultValue(method.getReturnType());
        });
    Arrow arrow = (Arrow) Proxy.newProxyInstance(
      Arrow.class.getClassLoader(), new Class<?>[] {Arrow.class}, (proxy, method, arguments) -> {
        if (method.getName().equals("setBasePotionData")
          || method.getName().equals("addCustomEffect") || method.getName().equals("setColor"))
          calls.add(new Object[] {method.getName(), arguments});
        return defaultValue(method.getReturnType());
      });

    EnchantmentEffectsListener.applyTippedArrowEffects(arrow, potion);

    assertEquals("setBasePotionData", calls.get(0)[0]);
    assertEquals(base, ((Object[]) calls.get(0)[1])[0]);
    assertEquals("addCustomEffect", calls.get(1)[0]);
    assertEquals(custom, ((Object[]) calls.get(1)[1])[0]);
    assertEquals(true, ((Object[]) calls.get(1)[1])[1]);
    assertEquals("setColor", calls.get(2)[0]);
    assertEquals(color, ((Object[]) calls.get(2)[1])[0]);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0F;
    if (type == double.class) return 0D;
    if (type == char.class) return '\0';
    return null;
  }

  @Test void selectorGridCentersRowsWithoutChangingSocketOrder() {
    assertEquals(31, EnchantingMenuController.gridPosition(0, 1));
    assertEquals(30, EnchantingMenuController.gridPosition(0, 3));
    assertEquals(32, EnchantingMenuController.gridPosition(2, 3));
    assertEquals(29, EnchantingMenuController.gridPosition(0, 7));
    assertEquals(33, EnchantingMenuController.gridPosition(4, 7));
    assertEquals(39, EnchantingMenuController.gridPosition(5, 7));
    assertEquals(40, EnchantingMenuController.gridPosition(6, 7));
  }

  @Test void rawBooksExposeTheCenteredUniversalSocket() {
    SocketedBookItem book = new SocketedBookItem(Material.BOOK);
    assertTrue(SocketedVanillaItem.supports(Material.BOOK));
    assertEquals(List.of(EnchantmentCategory.UNIVERSAL), SocketLayouts.forMaterial(Material.BOOK));
    assertEquals(1, book.sockets().size());
    assertEquals(EnchantmentCategory.UNIVERSAL, book.sockets().get(0).category());
    assertTrue(book.sockets().get(0).accepts(new LethalityEnchantment()));
  }

  @Test void registeredSocketItemsUseDedicatedOverrideTypes() {
    assertEquals("IronSword", SocketedItemOverrides.create(Material.IRON_SWORD).getClass().getSimpleName());
    assertEquals("GoldenPickaxe", SocketedItemOverrides.create(Material.GOLDEN_PICKAXE).getClass().getSimpleName());
  }

  @Test void exactAllowlistExcludesWoodAndUnlistedNetheritePickaxe() {
    assertFalse(SocketedVanillaItem.supports(Material.WOODEN_SWORD));
    assertFalse(SocketedVanillaItem.supports(Material.NETHERITE_PICKAXE));
    assertTrue(SocketedVanillaItem.supports(Material.IRON_SWORD));
  }

  @Test void layoutsUseGlobalCategoryOrderAndPreserveRepeatedSockets() {
    assertEquals(List.of(
      EnchantmentCategory.FATALITY,
      EnchantmentCategory.PROWESS,
      EnchantmentCategory.HARVESTING,
      EnchantmentCategory.HARVESTING,
      EnchantmentCategory.SUSTAINABILITY,
      EnchantmentCategory.SUSTAINABILITY
    ), SocketLayouts.forMaterial(Material.GOLDEN_AXE));
    assertEquals(List.of(
      EnchantmentCategory.TIDE,
      EnchantmentCategory.TIDE,
      EnchantmentCategory.HARVESTING,
      EnchantmentCategory.HARVESTING,
      EnchantmentCategory.HARVESTING
    ), SocketLayouts.forMaterial(Material.FISHING_ROD));
  }

  @Test void everyDiamondToolHasASustainabilitySocket() {
    for (Material material : List.of(
      Material.DIAMOND_SWORD,
      Material.DIAMOND_AXE,
      Material.DIAMOND_PICKAXE,
      Material.DIAMOND_SHOVEL,
      Material.DIAMOND_HOE
    )) assertTrue(SocketLayouts.forMaterial(material).contains(EnchantmentCategory.SUSTAINABILITY), material.name());
  }

  @Test void repeatedSocketEnchantmentsRemainDistinctAndStackTheirLevels() {
    var repeated = new TestEfficiency();
    var first = new EnchantmentSelection(repeated, new EnchantmentData(1));
    var second = new EnchantmentSelection(repeated, new EnchantmentData(1));
    EnchantmentOffer offer = new EnchantmentOffer(30, 32, 1D, List.of(first, second), List.of());

    assertEquals(2, offer.selections().size());
    assertEquals(2, SocketedVanillaItem.aggregateLevels(List.of(
      Map.entry(repeated, first.data()), Map.entry(repeated, second.data())
    )).get(repeated));
    assertTrue(EnchantmentCatalog.repeatable(repeated));
    assertFalse(EnchantmentCatalog.repeatable(new LethalityEnchantment()));

    ArrayList<Map.Entry<dev.bisz.items.DevEnchantment, EnchantmentData>> invalidDuplicates =
      new ArrayList<>(List.of(
        Map.entry(new LethalityEnchantment(), new EnchantmentData(1)),
        Map.entry(new LethalityEnchantment(), new EnchantmentData(1))
      ));
    SocketedVanillaItem.collapseNonRepeatableEnchantments(invalidDuplicates);
    assertEquals(1, invalidDuplicates.size());
    assertEquals(2, invalidDuplicates.get(0).getValue().level());
  }

  @Test void customCatalogUsesRequestedCategories() {
    var definitions = EnchantmentCatalog.customDefinitions();
    assertEquals(List.of(
      LethalityEnchantment.class,
      PenetrationEnchantment.class,
      InflameEnchantment.class,
      KnockbackEnchantment.class,
      NimbleEnchantment.class,
      AcrobaticsEnchantment.class,
      WingedEnchantment.class,
      ImpactResistanceEnchantment.class
    ), definitions.stream().map(Object::getClass).toList());
    assertEquals(EnchantmentCategory.FATALITY, EnchantmentCatalog.category(definitions.get(0)));
    assertEquals(EnchantmentCategory.PROWESS, EnchantmentCatalog.category(definitions.get(2)));
    assertEquals(EnchantmentCategory.MOBILITY, EnchantmentCatalog.category(definitions.get(6)));
    assertTrue(definitions.stream().noneMatch(enchantment ->
      enchantment.properties().handTicking() || enchantment.properties().inventoryTicking()));
  }

  @Test void wingedOwnsTheDeclarativeDoubleJumpAbility() {
    var ability = WingedEnchantment.DOUBLE_JUMP;
    assertEquals("Double Jump", ability.displayName(null));
    assertEquals("Allows you to jump in the air.", ability.displayDescription(null));
    assertEquals(Quality.COMMON, ability.quality());
    assertEquals(AbilityUsageMethod.DOUBLE_JUMP, ability.usageMethod());
    assertEquals(3D, ability.cooldownSeconds());
    assertEquals(List.of(ability), SocketedVanillaItem.abilitiesFor(List.of(new WingedEnchantment())));
    assertTrue(SocketedVanillaItem.abilitiesFor(List.of(new LethalityEnchantment())).isEmpty());
    assertEquals(List.of(ability), SocketedVanillaItem.abilitiesFor(
      List.of(new LethalityEnchantment()), List.of(new WingedEnchantment())));
    assertEquals(List.of(NimbleEnchantment.SHORTBOW), SocketedVanillaItem.abilitiesFor(
      Material.BOW, List.of(new NimbleEnchantment())));
    assertEquals(AbilityUsageMethod.LEFT_CLICK, NimbleEnchantment.SHORTBOW.usageMethod());
    assertEquals(.2D, NimbleEnchantment.SHORTBOW.cooldownSeconds());
    assertTrue(SocketedVanillaItem.abilitiesFor(Material.CROSSBOW, List.of(new NimbleEnchantment())).isEmpty());
  }

  @Test void riptideFishingRodsExposeGrappleAndUseVanillaScaledPull() {
    var ability = RiptideFishingRodSpecialty.GRAPPLE;
    assertEquals("Grapple", ability.displayName(null));
    assertEquals("Pulls you toward the fishing hook while in water or rain.", ability.displayDescription(null));
    assertEquals(Quality.COMMON, ability.quality());
    assertEquals(AbilityUsageMethod.REEL_IN, ability.usageMethod());
    assertEquals(List.of(ability), SocketedVanillaItem.abilitiesFor(
      Material.FISHING_ROD, List.of(new TestRiptide())));
    assertTrue(SocketedVanillaItem.abilitiesFor(Material.TRIDENT, List.of(new TestRiptide())).isEmpty());

    var velocity = RiptideFishingRodSpecialty.pullVelocity(new org.bukkit.util.Vector(3D, 4D, 0D), 1);
    assertEquals(.9D, velocity.getX(), 0.000_001D);
    assertEquals(1.35D, velocity.getY(), 0.000_001D);
    assertEquals(0D, velocity.getZ());
    assertTrue(EnchantmentEffectsListener.grappleEnvironment(true, false, -.5D, 0D, false));
    assertTrue(EnchantmentEffectsListener.grappleEnvironment(false, true, .8D, .4D, true));
    assertFalse(EnchantmentEffectsListener.grappleEnvironment(false, true, -.2D, .4D, true));
    assertFalse(EnchantmentEffectsListener.grappleEnvironment(false, true, 2D, 0D, true));
    assertFalse(EnchantmentEffectsListener.grappleEnvironment(false, true, .8D, .4D, false));
    assertTrue(EnchantmentEffectsListener.consumesDurability(0, new java.util.Random(1)));
    assertFalse(EnchantmentEffectsListener.consumesDurability(2,
      new java.util.Random() { @Override public int nextInt(int bound) { return 1; } }));
  }

  @Test void projectileDescriptionsExcludeMeleeOnlyEffects() {
    assertEquals("Increases damage by 1 in melee or 50% with projectiles.",
      new LethalityEnchantment().displayDescription(null, new EnchantmentData(1)));
    assertEquals("Increases projectile damage by 50%.",
      new LethalityEnchantment().displayDescription(null, new EnchantmentData(1), Material.BOW));
    assertEquals("Increases melee damage by 1.",
      new LethalityEnchantment().displayDescription(null, new EnchantmentData(1), Material.IRON_SWORD));
    assertEquals("Sets projectile targets ablaze for 3 seconds.",
      new InflameEnchantment().displayDescription(null, new EnchantmentData(1), Material.CROSSBOW));
    assertEquals("Increases projectile knockback by 1.",
      new KnockbackEnchantment().displayDescription(null, new EnchantmentData(1), Material.BOW));
  }

  @Test void projectileInflameUsesItsProjectileDurationWithoutShorteningExistingFire() {
    assertEquals(60, InflameEnchantment.projectileFireTicks(1));
    assertEquals(100, InflameEnchantment.projectileFireTicks(2));
    assertEquals(100, InflameEnchantment.projectileFireTicks(5));
    assertEquals(60, InflameEnchantment.resultingFireTicks(0, 1, true));
    assertEquals(140, InflameEnchantment.resultingFireTicks(140, 1, true));
    assertEquals(160, InflameEnchantment.resultingFireTicks(0, 2, false));
  }

  @Test void wingedCooldownUsesAbilityOwnedFifteenSegmentActionBar() {
    AbilityCooldown cooldown = AbilityCooldown.start(WingedEnchantment.DOUBLE_JUMP, 1_000L);
    String full = cooldown.actionBar(null, 1_000L);
    String half = cooldown.actionBar(null, 2_500L);
    assertEquals(15, full.chars().filter(character -> character == '|').count());
    assertEquals(15, half.chars().filter(character -> character == '|').count());
    assertTrue(full.contains("§e3.0s"));
    assertTrue(half.contains("§e1.5s"));
    assertTrue(EnchantmentEffectsListener.wingedProtectionActive(cooldown, 1_000L));
    assertFalse(EnchantmentEffectsListener.wingedProtectionActive(cooldown, 4_000L));
    assertFalse(EnchantmentEffectsListener.wingedProtectionActive(null, 1_000L));
    assertTrue(EnchantmentEffectsListener.wingedProtectionActive(cooldown, false, 1_000L));
    assertFalse(EnchantmentEffectsListener.wingedProtectionActive(cooldown, false, 4_000L));
    assertTrue(EnchantmentEffectsListener.wingedProtectionActive(cooldown, true, 4_000L));
    assertTrue(EnchantmentEffectsListener.wingedProtectionActive(null, true, 4_000L));
    var vertical = EnchantmentEffectsListener.wingedLaunchVelocity(
      new org.bukkit.util.Vector(.01D, -.1D, 0D), new org.bukkit.util.Vector(1D, 0D, 0D));
    assertEquals(.01D, vertical.getX());
    assertEquals(.75D, vertical.getY(), 0.000_001D);
    assertEquals(0D, vertical.getZ());
    var sprinting = EnchantmentEffectsListener.wingedLaunchVelocity(
      new org.bukkit.util.Vector(.05D, -.1D, 0D), new org.bukkit.util.Vector(1D, .5D, 0D));
    assertEquals(.25D, sprinting.getX(), 0.000_001D);
    assertEquals(.7D, sprinting.getY());
    assertEquals(0D, sprinting.getZ());
    assertTrue(full.startsWith("§fDouble Jump §8-"));
  }

  @Test void filledSocketStartsANewBlockAfterAnEmptySocket() {
    ArrayList<String> afterEmpty = new ArrayList<>(List.of("empty socket"));
    SocketedVanillaItem.separateFilledSocket(afterEmpty);
    assertEquals(List.of("empty socket", ""), afterEmpty);

    ArrayList<String> alreadySeparated = new ArrayList<>(List.of("description", ""));
    SocketedVanillaItem.separateFilledSocket(alreadySeparated);
    assertEquals(List.of("description", ""), alreadySeparated);

    ArrayList<String> firstSocket = new ArrayList<>();
    SocketedVanillaItem.separateFilledSocket(firstSocket);
    assertTrue(firstSocket.isEmpty());

    ArrayList<String> withSocketBelow = new ArrayList<>(List.of("description"));
    SocketedVanillaItem.finishFilledSocket(withSocketBelow, true);
    assertEquals(List.of("description", ""), withSocketBelow);

    ArrayList<String> finalSocket = new ArrayList<>(List.of("description"));
    SocketedVanillaItem.finishFilledSocket(finalSocket, false);
    assertEquals(List.of("description"), finalSocket);
  }

  @Test void commandAddedEnchantmentsAreAdoptedIntoStoredSockets() {
    var lethality = new LethalityEnchantment();
    var inflame = new InflameEnchantment();
    ArrayList<Map.Entry<dev.bisz.items.DevEnchantment, EnchantmentData>> stored =
      new ArrayList<>(List.of(Map.entry(lethality, new EnchantmentData(1))));
    Map<dev.bisz.items.DevEnchantment, EnchantmentData> actual = new LinkedHashMap<>();
    actual.put(lethality, new EnchantmentData(1));
    actual.put(inflame, new EnchantmentData(2));

    SocketedVanillaItem.appendUnstoredEnchantments(stored, actual);

    assertEquals(List.of(lethality, inflame), stored.stream().map(Map.Entry::getKey).toList());
  }

  private static final class TestRiptide extends CustomEnchantment {
    private TestRiptide() {
      super(EnchantmentId.of("minecraft", "riptide"), EnchantmentProperties.builder().build());
    }
  }

  private static final class TestEfficiency extends CustomEnchantment {
    private TestEfficiency() {
      super(EnchantmentId.of("minecraft", "efficiency"), EnchantmentProperties.builder().build());
    }
  }
}
