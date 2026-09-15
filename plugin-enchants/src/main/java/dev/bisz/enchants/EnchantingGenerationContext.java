package dev.bisz.enchants;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.bukkit.inventory.ItemStack;

/** Immutable inputs supplied to an enchantment generator. */
public record EnchantingGenerationContext(
  ItemStack item,
  List<EnchantingModifierData> modifiers,
  RandomGenerator random,
  int selectedSocket
) {
  public EnchantingGenerationContext {
    item = Objects.requireNonNull(item, "item").clone();
    modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
    random = Objects.requireNonNull(random, "random");
    if (selectedSocket < 0) throw new IllegalArgumentException("selectedSocket must be a socket index");
  }
  /** Compatibility constructor targeting the first socket. */
  public EnchantingGenerationContext(ItemStack item, List<EnchantingModifierData> modifiers, RandomGenerator random) {
    this(item, modifiers, random, 0);
  }
  @Override public ItemStack item() { return item.clone(); }
}
