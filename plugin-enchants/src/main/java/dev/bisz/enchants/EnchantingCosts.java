package dev.bisz.enchants;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

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
    FileConfiguration costs = config.costs();
    FileConfiguration main = config.config();
    return new EnchantingCosts(
      section(costs, "materials"),
      section(costs, "items"),
      main.getDouble("cost.multiplier", 1.0),
      main.getInt("cost.refund-percent", 80),
      main.getInt("enchanting.lapis-cost", 3));
  }

  static EnchantingCosts from(Map<String, Object> values) {
    Map<String, Integer> materials = new HashMap<>();
    Map<String, Integer> items = new HashMap<>();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      if (entry.getValue() instanceof Number number) {
        materials.put(entry.getKey().toLowerCase(Locale.ROOT), number.intValue());
      }
    }
    return new EnchantingCosts(materials, items, 1.0, 80, 3);
  }

  /** The constant level cost of enchanting one socket on this item. */
  int materialCost(Material material) {
    Objects.requireNonNull(material, "material");
    String exact = material.getKey().getKey().toLowerCase(Locale.ROOT);
    Integer configured = items.get(exact);
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

  private static Map<String, Integer> section(FileConfiguration config, String path) {
    Map<String, Integer> values = new HashMap<>();
    ConfigurationSection root = config.getConfigurationSection(path);
    if (root == null) return values;
    for (String key : root.getKeys(false)) {
      values.put(key.toLowerCase(Locale.ROOT), root.getInt(key));
    }
    return values;
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
