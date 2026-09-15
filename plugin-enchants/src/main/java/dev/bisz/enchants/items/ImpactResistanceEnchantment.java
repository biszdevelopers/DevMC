package dev.bisz.enchants.items;

/** Heavy-impact protection definition. */
public final class ImpactResistanceEnchantment extends EnchantsEnchantment {
  public ImpactResistanceEnchantment() { super("impact_resistance"); }
  @Override protected String fallbackDescription(int level) {
    return "Reduces heavy impact damage (available in the combat update).";
  }
}
