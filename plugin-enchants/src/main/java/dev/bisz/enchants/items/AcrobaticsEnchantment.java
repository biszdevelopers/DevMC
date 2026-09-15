package dev.bisz.enchants.items;

/** Mace smash launch and spear reach definition. */
public final class AcrobaticsEnchantment extends EnchantsEnchantment {
  public AcrobaticsEnchantment() { super("acrobatics"); }
  @Override protected String fallbackDescription(int level) {
    return "Improves mace smash launch and spear reach (available with those item mechanics).";
  }
}
