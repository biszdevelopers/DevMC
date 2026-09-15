package dev.bisz.enchants;

import dev.bisz.items.DevEnchantment;
import dev.bisz.items.EnchantmentData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One generated offer. Selection lists preserve repeated socket enchantments. */
public final class EnchantmentOffer {
  private final int experienceLevels;
  private final int lapisLazuli;
  private final double qualityMultiplier;
  private final List<EnchantmentSelection> baseSelections;
  private final List<EnchantmentSelection> extraSelections;

  public EnchantmentOffer(
    int experienceLevels,
    int lapisLazuli,
    double qualityMultiplier,
    Map<DevEnchantment, EnchantmentData> baseEnchantments,
    Map<DevEnchantment, EnchantmentData> extraEnchantments
  ) {
    this(experienceLevels, lapisLazuli, qualityMultiplier,
      selections(baseEnchantments), selections(extraEnchantments));
  }

  /** Constructor for socket-aware generators, whose lists may repeat an enchantment. */
  public EnchantmentOffer(
    int experienceLevels,
    int lapisLazuli,
    double qualityMultiplier,
    List<EnchantmentSelection> baseSelections,
    List<EnchantmentSelection> extraSelections
  ) {
    if (experienceLevels < 1 || lapisLazuli < 1)
      throw new IllegalArgumentException("Offer costs must be positive");
    if (!Double.isFinite(qualityMultiplier) || qualityMultiplier <= 0.0)
      throw new IllegalArgumentException("Cost multiplier must be positive and finite");
    this.experienceLevels = experienceLevels;
    this.lapisLazuli = lapisLazuli;
    this.qualityMultiplier = qualityMultiplier;
    this.baseSelections = List.copyOf(baseSelections);
    this.extraSelections = List.copyOf(extraSelections);
    if (this.baseSelections.isEmpty() && this.extraSelections.isEmpty())
      throw new IllegalArgumentException("An offer needs an enchantment");
  }

  /** Backward-compatible constructor for map-based generators. */
  public EnchantmentOffer(
    int experienceLevels,
    int lapisLazuli,
    Map<DevEnchantment, EnchantmentData> baseEnchantments,
    Map<DevEnchantment, EnchantmentData> extraEnchantments
  ) {
    this(experienceLevels, lapisLazuli, 1.0, baseEnchantments, extraEnchantments);
  }

  public int experienceLevels() { return experienceLevels; }
  public int lapisLazuli() { return lapisLazuli; }
  public double qualityMultiplier() { return qualityMultiplier; }
  public List<EnchantmentSelection> baseSelections() { return baseSelections; }
  public List<EnchantmentSelection> extraSelections() { return extraSelections; }

  /** Compatibility view. Repeated IDs collapse; use {@link #selections()} for socket work. */
  public Map<DevEnchantment, EnchantmentData> baseEnchantments() { return asMap(baseSelections); }

  /** Compatibility view. Repeated IDs collapse; use {@link #selections()} for socket work. */
  public Map<DevEnchantment, EnchantmentData> extraEnchantments() { return asMap(extraSelections); }

  /** All selections in application order, including repeated enchantments. */
  public List<EnchantmentSelection> selections() {
    ArrayList<EnchantmentSelection> result = new ArrayList<>(baseSelections);
    result.addAll(extraSelections);
    return List.copyOf(result);
  }

  /** Compatibility view for integrations that do not support repeated entries. */
  public Map<DevEnchantment, EnchantmentData> enchantments() { return asMap(selections()); }

  private static List<EnchantmentSelection> selections(Map<DevEnchantment, EnchantmentData> values) {
    return values.entrySet().stream()
      .map(entry -> new EnchantmentSelection(entry.getKey(), entry.getValue())).toList();
  }

  private static Map<DevEnchantment, EnchantmentData> asMap(List<EnchantmentSelection> selections) {
    LinkedHashMap<DevEnchantment, EnchantmentData> result = new LinkedHashMap<>();
    selections.forEach(selection -> result.put(selection.enchantment(), selection.data()));
    return Map.copyOf(result);
  }
}
