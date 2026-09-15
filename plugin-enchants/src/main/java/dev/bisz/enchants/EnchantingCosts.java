package dev.bisz.enchants;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.bukkit.Material;

/** Config-driven material costs, refunds, and constant per-item enchant prices. */
final class EnchantingCosts {
  private final Map<String, Integer> materials;
  private final Map<String, Integer> items;
  private final double multiplier;
  private final int refundPercent;
  private final int lapisCost;

  private EnchantingCosts(
    Map<String, Integer> materials, Map<String, Integer> items,
    double multiplier, int refundPercent, int lapisCost
  ) {
    this.materials = Map.copyOf(materials);
    this.items = Map.copyOf(items);
    this.multiplier = multiplier;
    this.refundPercent = refundPercent;
    this.lapisCost = Math.max(0, lapisCost);
  }

  static EnchantingCosts load(EnchantsConfig config) {
    Map<String, Integer> materials = new HashMap<>();
    Map<String, Integer> items = new HashMap<>();
    Map<String, Object> costs = config.costs();
    Map<String, Object> configuredMaterials = EnchantsConfig.section(costs, "materials");
    Map<String, Object> configuredItems = EnchantsConfig.section(costs, "items");
    if (configuredMaterials.isEmpty() && configuredItems.isEmpty()) {
      // Legacy flat costs.json stores every tier and item override together.
      numeric(costs, materials);
    } else {
      numeric(configuredMaterials, materials);
      numeric(configuredItems, items);
    }
    Map<String, Object> main = config.config();
    return new EnchantingCosts(
      materials,
      items,
      EnchantsConfig.decimal(main, "cost.multiplier", 1.0),
      EnchantsConfig.integer(main, "cost.refund-percent", 80),
      EnchantsConfig.integer(main, "enchanting.lapis-cost", 3));
  }

  private static void numeric(Map<String, Object> values, Map<String, Integer> target) {
    values.forEach((key, value) -> {
      if (value instanceof Number number) target.put(key.toLowerCase(Locale.ROOT), number.intValue());
    });
  }

  /** The constant level cost of enchanting one socket on this item. */
  int materialCost(Material material) {
    Objects.requireNonNull(material, "material");
    String exact = material.getKey().getKey().toLowerCase(Locale.ROOT);
    Integer configured = items.get(exact);
    if (configured == null) configured = materials.get(exact);
    if (configured == null) configured = materials.get(tier(material, exact));
    if (configured == null) configured = materials.getOrDefault("default", 0);
    return clampCost((int) Math.round(configured * multiplier));
  }

  /** The level refund granted when a socket is stripped. */
  int refund(Material material) {
    return (int) Math.round(materialCost(material) * (refundPercent / 100D));
  }

  OfferCost roll(Material material, RandomGenerator random) {
    return new OfferCost(materialCost(material), lapisCost);
  }

  static OfferCost roll(int materialCost, RandomGenerator random) {
    return new OfferCost(clampCost(materialCost), 1);
  }

  private static int clampCost(int value) { return Math.max(0, Math.min(25, value)); }

  private static String tier(Material material, String key) {
    if (material == Material.TURTLE_HELMET) return "turtle";
    if (key.startsWith("wooden_")) return "wood";
    if (key.startsWith("stone_")) return "stone";
    if (key.startsWith("copper_")) return "copper";
    if (key.startsWith("iron_")) return "iron";
    if (key.startsWith("diamond_")) return "diamond";
    if (key.startsWith("golden_")) return "gold";
    if (key.startsWith("netherite_")) return "netherite";
    if (key.startsWith("leather_")) return "leather";
    if (key.startsWith("chainmail_")) return "chainmail";
    return "default";
  }

  record OfferCost(int experienceLevels, int lapisLazuli) {}
}
