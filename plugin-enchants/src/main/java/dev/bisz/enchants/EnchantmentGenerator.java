package dev.bisz.enchants;

import java.util.List;

/** Adjustable entrypoint for generating the enchanting table's offers. */
@FunctionalInterface
public interface EnchantmentGenerator {
  List<EnchantmentOffer> generate(EnchantingGenerationContext context);
}
