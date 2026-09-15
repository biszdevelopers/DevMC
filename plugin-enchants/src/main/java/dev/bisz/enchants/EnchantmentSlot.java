package dev.bisz.enchants;

import dev.bisz.items.DevEnchantment;
import java.util.Objects;
import java.util.function.Predicate;

/** One ordered enchantment socket on a socketed vanilla item definition. */
public final class EnchantmentSlot {
  private final int index;
  private final EnchantmentCategory category;
  private final Predicate<DevEnchantment> acceptance;

  public EnchantmentSlot(int index, EnchantmentCategory category, Predicate<DevEnchantment> acceptance) {
    if (index < 0) throw new IllegalArgumentException("Slot index cannot be negative");
    this.index = index;
    this.category = Objects.requireNonNull(category, "category");
    this.acceptance = Objects.requireNonNull(acceptance, "acceptance");
  }

  public int index() { return index; }
  public EnchantmentCategory category() { return category; }

  public boolean accepts(DevEnchantment enchantment) {
    return acceptance.test(Objects.requireNonNull(enchantment, "enchantment"));
  }

  public static EnchantmentSlot typed(int index, EnchantmentCategory category) {
    return new EnchantmentSlot(index, category, enchantment -> EnchantmentCatalog.category(enchantment) == category);
  }

  /** A socket that accepts every enchantment offered by this plugin. */
  public static EnchantmentSlot universal(int index) {
    return new EnchantmentSlot(index, EnchantmentCategory.UNIVERSAL, EnchantmentCatalog::offered);
  }
}
