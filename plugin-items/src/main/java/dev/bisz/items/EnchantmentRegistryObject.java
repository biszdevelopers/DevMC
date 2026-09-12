package dev.bisz.items;

import java.util.Objects;
import java.util.function.Supplier;

/** Deferred handle for a custom enchantment definition. */
public final class EnchantmentRegistryObject<T extends CustomEnchantment> implements Supplier<T> {
  private final EnchantmentId id;
  private T value;
  EnchantmentRegistryObject(EnchantmentId id) { this.id = Objects.requireNonNull(id, "id"); }
  public EnchantmentId id() { return id; }
  @Override public T get() {
    if (value == null) throw new IllegalStateException("Enchantment has not been registered: " + id);
    return value;
  }
  void resolve(T registered) {
    if (value != null) throw new IllegalStateException("Enchantment already resolved: " + id);
    value = registered;
  }
}
