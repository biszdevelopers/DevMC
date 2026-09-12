package dev.bisz.items;

/** Base class for plugin-defined DevEnchantments. */
public abstract class CustomEnchantment extends DevEnchantment {
  protected CustomEnchantment(EnchantmentId id, EnchantmentProperties properties) { super(id, properties); }
  @Override public final boolean vanilla() { return false; }
}
