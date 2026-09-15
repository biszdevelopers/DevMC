package dev.bisz.enchants;

import dev.bisz.items.DevEnchantment;
import dev.bisz.items.EnchantmentData;
import java.util.Objects;

/** One enchantment assigned to one socket within an enchanting-table offer. */
public record EnchantmentSelection(DevEnchantment enchantment, EnchantmentData data) {
  public EnchantmentSelection {
    Objects.requireNonNull(enchantment, "enchantment");
    Objects.requireNonNull(data, "data");
  }
}
