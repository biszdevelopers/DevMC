package dev.bisz.enchants;

import dev.bisz.items.DevEnchantment;
import dev.bisz.items.EnchantmentData;
import java.util.LinkedHashMap;
import java.util.Map;

/** One generated menu offer, split into ordinary and modifier-only preview groups. */
public record EnchantmentOffer(
  int experienceLevels,
  int lapisLazuli,
  double qualityMultiplier,
  Map<DevEnchantment, EnchantmentData> baseEnchantments,
  Map<DevEnchantment, EnchantmentData> extraEnchantments
) {
  public EnchantmentOffer {
    if (experienceLevels < 1 || lapisLazuli < 1) throw new IllegalArgumentException("Offer costs must be positive");
    if (!Double.isFinite(qualityMultiplier) || qualityMultiplier <= 0.0) {
      throw new IllegalArgumentException("Cost multiplier must be positive and finite");
    }
    baseEnchantments = Map.copyOf(new LinkedHashMap<>(baseEnchantments));
    extraEnchantments = Map.copyOf(new LinkedHashMap<>(extraEnchantments));
    if (baseEnchantments.isEmpty() && extraEnchantments.isEmpty()) throw new IllegalArgumentException("An offer needs an enchantment");
  }

  /** Backward-compatible constructor for generators without quality scaling. */
  public EnchantmentOffer(
    int experienceLevels,
    int lapisLazuli,
    Map<DevEnchantment, EnchantmentData> baseEnchantments,
    Map<DevEnchantment, EnchantmentData> extraEnchantments
  ) {
    this(experienceLevels, lapisLazuli, 1.0, baseEnchantments, extraEnchantments);
  }

  public Map<DevEnchantment, EnchantmentData> enchantments() {
    LinkedHashMap<DevEnchantment, EnchantmentData> result = new LinkedHashMap<>(baseEnchantments);
    result.putAll(extraEnchantments);
    return Map.copyOf(result);
  }
}
