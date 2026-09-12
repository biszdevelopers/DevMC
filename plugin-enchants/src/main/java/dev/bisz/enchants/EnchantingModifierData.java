package dev.bisz.enchants;

/** Statistics exposed to enchantment generators for one modifier type. */
public interface EnchantingModifierData {
  String modifierId();
  int blockCount();
}
