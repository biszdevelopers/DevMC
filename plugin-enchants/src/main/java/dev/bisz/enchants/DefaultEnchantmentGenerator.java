package dev.bisz.enchants;

import dev.bisz.items.DevEnchantment;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.items.VanillaEnchantment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/** Built-in tiered vanilla generator with bookshelf-driven extra rolls. */
public final class DefaultEnchantmentGenerator implements EnchantmentGenerator {
  private static final NamespacedKey EXTRA_ROLL = Objects.requireNonNull(
    NamespacedKey.fromString("enchants:extra_roll")
  );
  private static final int[] LAPIS_MIN = { 1, 11, 21 };
  private static final int[] LAPIS_MAX = { 10, 20, 30 };
  private static final int[] LEVEL_MIN = { 1, 6, 11 };
  private static final int[] LEVEL_MAX = { 5, 10, 15 };
  private static final int[] EXTRA_ROLL_CAP = { 1, 2, 5 };
  private static final int MAX_BOOKSHELF_PERCENT = 500;

  @Override
  public List<EnchantmentOffer> generate(EnchantingGenerationContext context) {
    ItemStack item = context.item();
    if (!ItemsPlugin.instance().factory().wrap(item).enchantments().isEmpty()) return List.of();
    List<VanillaEnchantment> candidates = candidates(item);
    VanillaEnchantment mending = mending(item);
    if (candidates.isEmpty() && mending == null) return List.of();
    RandomGenerator random = context.random();
    int extraPercent = context.modifiers().stream()
      .filter(BookshelfModifierData.class::isInstance)
      .map(BookshelfModifierData.class::cast)
      .mapToInt(BookshelfModifierData::extraChancePercent)
      .sum();
    DevEnchantment rainbow = ItemsPlugin.instance().enchantments()
      .get(EnchantmentId.of("items", "rainbow")).orElse(null);
    ArrayList<EnchantmentOffer> offers = new ArrayList<>(3);
    for (int tier = 0; tier < 3; tier++) {
      LinkedHashMap<DevEnchantment, EnchantmentData> base = selectBase(candidates, mending, tier + 1, random);
      if (rainbow != null && random.nextBoolean()) base.put(rainbow, new EnchantmentData(1));
      LinkedHashMap<DevEnchantment, EnchantmentData> extras = new LinkedHashMap<>();
      int rolls = extraRollsForTier(extraPercent, tier, random);
      for (int roll = 0; roll < rolls; roll++) {
        VanillaEnchantment selected = rollExtra(candidates, mending, random);
        if (selected == null) continue;
        EnchantmentData existing = base.get(selected);
        if (existing != null) {
          base.put(selected, extra(existing));
          continue;
        }
        existing = extras.get(selected);
        if (existing != null) extras.put(selected, extra(existing));
        else extras.put(selected, new EnchantmentData(1, Map.of(EXTRA_ROLL, true)));
      }
      double multiplier = costMultiplier(base, extras);
      int baseLevels = random.nextInt(LEVEL_MIN[tier], LEVEL_MAX[tier] + 1);
      int baseLapis = random.nextInt(LAPIS_MIN[tier], LAPIS_MAX[tier] + 1);
      offers.add(new EnchantmentOffer(
        scaledCost(baseLevels, multiplier),
        scaledCost(baseLapis, multiplier),
        multiplier,
        base,
        extras
      ));
    }
    return List.copyOf(offers);
  }

  static int extraRolls(int percentage, RandomGenerator random) {
    if (percentage <= 0) return 0;
    int guaranteed = percentage / 100;
    int remainder = percentage % 100;
    return guaranteed + (remainder > 0 && random.nextInt(100) < remainder ? 1 : 0);
  }

  static int extraRollsForTier(int percentage, int tier, RandomGenerator random) {
    if (tier < 0 || tier >= EXTRA_ROLL_CAP.length) throw new IllegalArgumentException("Unknown offer tier: " + tier);
    int bookshelfRolls = extraRolls(Math.min(percentage, MAX_BOOKSHELF_PERCENT), random);
    return Math.min(bookshelfRolls, EXTRA_ROLL_CAP[tier]);
  }

  static int extraRollCap(int tier) {
    if (tier < 0 || tier >= EXTRA_ROLL_CAP.length) throw new IllegalArgumentException("Unknown offer tier: " + tier);
    return EXTRA_ROLL_CAP[tier];
  }

  private static LinkedHashMap<DevEnchantment, EnchantmentData> selectBase(
    List<VanillaEnchantment> candidates,
    VanillaEnchantment mending,
    int desired,
    RandomGenerator random
  ) {
    ArrayList<VanillaEnchantment> remaining = new ArrayList<>(candidates);
    ArrayList<VanillaEnchantment> selected = new ArrayList<>();
    while (selected.size() < desired) {
      List<VanillaEnchantment> compatible = remaining.stream().filter(candidate ->
        selected.stream().noneMatch(existing -> conflicts(candidate.bukkit(), existing.bukkit()))
      ).toList();
      boolean mendingAvailable = mending != null && !selected.contains(mending) &&
        selected.stream().noneMatch(existing -> conflicts(mending.bukkit(), existing.bukkit()));
      VanillaEnchantment choice;
      if (mendingAvailable && random.nextInt(100) == 0) choice = mending;
      else if (!compatible.isEmpty()) choice = compatible.get(random.nextInt(compatible.size()));
      else break;
      selected.add(choice);
      remaining.remove(choice);
    }
    LinkedHashMap<DevEnchantment, EnchantmentData> result = new LinkedHashMap<>();
    selected.forEach(enchantment -> result.put(
      enchantment,
      new EnchantmentData(1)
    ));
    return result;
  }

  @SuppressWarnings("deprecation")
  private static List<VanillaEnchantment> candidates(ItemStack item) {
    return ItemsPlugin.instance().enchantments().values().stream()
      .filter(VanillaEnchantment.class::isInstance)
      .map(VanillaEnchantment.class::cast)
      .filter(enchantment -> !enchantment.bukkit().isTreasure() && !enchantment.bukkit().isCursed())
      .filter(enchantment -> applicable(enchantment, item))
      .sorted(Comparator.comparing(enchantment -> enchantment.id().toString()))
      .toList();
  }

  private static VanillaEnchantment mending(ItemStack item) {
    return ItemsPlugin.instance().enchantments().get(EnchantmentId.of("minecraft", "mending"))
      .filter(VanillaEnchantment.class::isInstance)
      .map(VanillaEnchantment.class::cast)
      .filter(enchantment -> applicable(enchantment, item))
      .orElse(null);
  }

  private static VanillaEnchantment rollExtra(
    List<VanillaEnchantment> candidates,
    VanillaEnchantment mending,
    RandomGenerator random
  ) {
    if (mending != null && random.nextInt(100) == 0) return mending;
    return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
  }

  static double costMultiplier(
    Map<DevEnchantment, EnchantmentData> base,
    Map<DevEnchantment, EnchantmentData> extras
  ) {
    LinkedHashMap<DevEnchantment, EnchantmentData> enchantments = new LinkedHashMap<>(base);
    enchantments.putAll(extras);
    int enchantmentCount = enchantments.size();
    long upgradedCount = enchantments.values().stream().filter(data -> data.level() > 1).count();
    return Math.log(4.134 * enchantmentCount + 2.0 * upgradedCount + Math.E);
  }

  static int scaledCost(int baseCost, double multiplier) {
    return Math.max(1, (int) Math.round(baseCost * multiplier));
  }

  private static boolean applicable(VanillaEnchantment enchantment, ItemStack item) {
    return item.getType() == Material.BOOK || enchantment.bukkit().canEnchantItem(item);
  }

  private static boolean conflicts(Enchantment left, Enchantment right) {
    return left.conflictsWith(right) || right.conflictsWith(left);
  }

  private static EnchantmentData extra(EnchantmentData current) {
    LinkedHashMap<NamespacedKey, Object> metadata = new LinkedHashMap<>(current.metadata());
    metadata.put(EXTRA_ROLL, true);
    return new EnchantmentData(Math.min(3999, current.level() + 1), metadata);
  }
}
