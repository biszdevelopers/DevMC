package dev.bisz.enchants;

import dev.bisz.bundler.JSON;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.bukkit.Material;

/** ServerData-backed material costs and deterministic offer-cost rolling. */
final class EnchantingCosts {
  static final String FILE = "enchants/costs.json";
  private static final int BASE_ENCHANTMENT_LEVEL_REQUIREMENT = 30;
  private final Map<String, Object> values;

  private EnchantingCosts(Map<String, Object> values) {
    this.values = Map.copyOf(values);
  }

  static EnchantingCosts load(EnchantsPlugin plugin) {
    try (InputStream defaults = plugin.getResource("costs.json")) {
      if (defaults == null) throw new IllegalStateException("Missing bundled enchantment cost defaults");
      JSON.mergeMissingDefaults(FILE, defaults);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot close bundled enchantment cost defaults", exception);
    }
    return new EnchantingCosts(JSON.loadDataFromDataBase(FILE));
  }

  static EnchantingCosts from(Map<String, Object> values) { return new EnchantingCosts(values); }

  int materialCost(Material material) {
    Objects.requireNonNull(material, "material");
    String exact = material.getKey().getKey().toLowerCase(Locale.ROOT);
    int configured = configured(exact, Integer.MIN_VALUE);
    if (configured != Integer.MIN_VALUE) return clampCost(configured);
    String tier = tier(material, exact);
    return clampCost(configured(tier, 0));
  }

  OfferCost roll(Material material, RandomGenerator random) {
    return roll(materialCost(material), random);
  }

  static OfferCost roll(int materialCost, RandomGenerator random) {
    materialCost = clampCost(materialCost);
    int total = materialCost * 2;
    int lapis = Math.max(1, (int) Math.ceil(Math.sqrt(total)) + random.nextInt(17));
    int levels = Math.max(1, BASE_ENCHANTMENT_LEVEL_REQUIREMENT
      + (int) Math.ceil(Math.cbrt(total)) + random.nextInt(11) - 5);
    return new OfferCost(levels, lapis);
  }

  private int configured(String key, int fallback) {
    Object value = values.get(key);
    return value instanceof Number number ? number.intValue() : fallback;
  }

  private static int clampCost(int value) { return clamp(value, 0, 25); }

  private static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

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
