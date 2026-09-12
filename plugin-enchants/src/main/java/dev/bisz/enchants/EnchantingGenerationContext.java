package dev.bisz.enchants;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.bukkit.inventory.ItemStack;

/** Immutable inputs supplied to an enchantment generator. */
public record EnchantingGenerationContext(
  ItemStack item,
  List<EnchantingModifierData> modifiers,
  RandomGenerator random
) {
  public EnchantingGenerationContext {
    item = Objects.requireNonNull(item, "item").clone();
    modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
    random = Objects.requireNonNull(random, "random");
  }
  @Override public ItemStack item() { return item.clone(); }
}
