package dev.bisz.enchants.items;

import dev.bisz.items.CustomEnchantment;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.EnchantmentProperties;
import org.bukkit.entity.Player;

/** Shared localization support for Enchants-owned enchantments. */
public abstract class EnchantsEnchantment extends CustomEnchantment {
  protected EnchantsEnchantment(String path) {
    super(EnchantmentId.of("enchants", path), EnchantmentProperties.builder().build());
  }

  @Override protected final String renderDescription(Player viewer, EnchantmentData data) {
    return translate(viewer, "enchantment.enchants." + id().path() + ".description",
      fallbackDescription(data.level()), descriptionArguments(data.level()));
  }

  protected abstract String fallbackDescription(int level);
  protected Object[] descriptionArguments(int level) { return new Object[0]; }
}
