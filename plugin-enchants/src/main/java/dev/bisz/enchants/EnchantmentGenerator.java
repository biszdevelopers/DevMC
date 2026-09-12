package dev.bisz.enchants;

import java.util.List;

/** Adjustable entrypoint for generating no offers or exactly three offers. */
@FunctionalInterface
public interface EnchantmentGenerator {
  List<EnchantmentOffer> generate(EnchantingGenerationContext context);
}
