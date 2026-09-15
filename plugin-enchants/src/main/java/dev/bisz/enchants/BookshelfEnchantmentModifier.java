package dev.bisz.enchants;

import org.bukkit.Material;

/** Permanent bookshelf modifier adding ten percent extra-roll chance per block. */
public final class BookshelfEnchantmentModifier extends CustomEnchantingModifier {
  public BookshelfEnchantmentModifier() { super(ModifierMode.PERMANENT); }
  @Override public String id() { return "enchants:bookshelves"; }
  @Override protected boolean matches(Material material) { return material == Material.BOOKSHELF; }
  @Override protected EnchantingModifierData createData(int count) { return new BookshelfModifierData(count, count * 10); }
  @Override protected int effectPercent(EnchantingModifierData data) { return ((BookshelfModifierData) data).extraChancePercent(); }
  @Override protected int maximumEffectPercent() { return 400; }
  @Override protected String nounLocaleKey() { return "enchants.modifier.bookshelves.noun"; }
  @Override protected String effectLocaleKey() { return "enchants.modifier.bookshelves.effect"; }
  @Override protected String descriptionLocaleKey() { return "enchants.modifier.bookshelves.description"; }
}
