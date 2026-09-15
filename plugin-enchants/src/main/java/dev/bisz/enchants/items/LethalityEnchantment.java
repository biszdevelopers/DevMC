package dev.bisz.enchants.items;

import dev.bisz.items.EnchantmentDamageContext;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Unified melee and projectile damage enchantment. */
public final class LethalityEnchantment extends EnchantsEnchantment {
  public LethalityEnchantment() { super("lethality"); }
  @Override protected String fallbackDescription(int level) { return "Increases damage by %s in melee or %d%% with projectiles."; }
  @Override protected Object[] descriptionArguments(int level) { return new Object[] { formatHalf(.5D + level * .5D), 25 + level * 25 }; }
  @Override public String displayDescription(Player viewer, dev.bisz.items.EnchantmentData data, Material material) {
    if (projectileWeapon(material)) return translate(viewer, "enchantment.enchants.lethality.projectile.description",
      "Increases projectile damage by %d%%.", 25 + data.level() * 25);
    return translate(viewer, "enchantment.enchants.lethality.melee.description",
      "Increases melee damage by %s.", formatHalf(.5D + data.level() * .5D));
  }
  @Override protected double modifyOutgoingDamage(EnchantmentDamageContext context, double damage) {
    return context.projectile() ? damage * (1.25D + context.level() * .25D) : damage + .5D + context.level() * .5D;
  }
  private static String formatHalf(double value) {
    return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
  }
  private static boolean projectileWeapon(Material material) { return material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT; }
}
