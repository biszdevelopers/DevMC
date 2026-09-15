package dev.bisz.enchants;

import dev.bisz.items.DevEnchantment;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.Material;

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

  /**
   * Whether this socket accepts the enchantment on an item of the given
   * material. Universal sockets accept every offered enchantment; typed
   * sockets additionally require the enchantment to fit the material.
   */
  public boolean accepts(DevEnchantment enchantment, Material material) {
    if (!accepts(enchantment)) return false;
    if (category == EnchantmentCategory.UNIVERSAL) return true;
    return EnchantmentCatalog.applicable(enchantment, Objects.requireNonNull(material, "material"));
  }

  /**
   * The category a socket presents while holding the given enchantment. A
   * filled universal socket adopts the enchantment's own category; typed
   * sockets keep their category.
   */
  public EnchantmentCategory filledCategory(DevEnchantment enchantment) {
    if (category != EnchantmentCategory.UNIVERSAL) return category;
    EnchantmentCategory actual = EnchantmentCatalog.category(Objects.requireNonNull(enchantment, "enchantment"));
    return actual == null ? category : actual;
  }

  public static EnchantmentSlot typed(int index, EnchantmentCategory category) {
    return new EnchantmentSlot(index, category, enchantment -> EnchantmentCatalog.category(enchantment) == category);
  }

  /** A socket that accepts every enchantment offered by this plugin. */
  public static EnchantmentSlot universal(int index) {
    return new EnchantmentSlot(index, EnchantmentCategory.UNIVERSAL, EnchantmentCatalog::offered);
  }
}
