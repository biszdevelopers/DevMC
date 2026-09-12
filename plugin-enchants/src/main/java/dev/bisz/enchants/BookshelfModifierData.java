package dev.bisz.enchants;

/** Bookshelf statistics; the percentage is intentionally uncapped. */
public record BookshelfModifierData(
  int blockCount,
  int extraChancePercent
) implements EnchantingModifierData {
  public BookshelfModifierData {
    if (blockCount < 0 || extraChancePercent < 0) throw new IllegalArgumentException(
      "Modifier statistics cannot be negative"
    );
  }
  @Override public String modifierId() { return "enchants:bookshelves"; }
}
