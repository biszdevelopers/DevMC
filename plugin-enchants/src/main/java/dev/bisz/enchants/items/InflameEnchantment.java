package dev.bisz.enchants.items;

import dev.bisz.items.EnchantmentDamageContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Unified melee and projectile fire enchantment. */
public final class InflameEnchantment extends EnchantsEnchantment {
  public InflameEnchantment() { super("inflame"); }
  @Override protected String fallbackDescription(int level) { return "Sets targets ablaze for %d seconds in melee or %d seconds with projectiles."; }
  @Override protected Object[] descriptionArguments(int level) { return new Object[] { level * 4, level == 1 ? 3 : 5 }; }
  @Override public String displayDescription(Player viewer, dev.bisz.items.EnchantmentData data, Material material) {
    if (projectileWeapon(material)) return translate(viewer, "enchantment.enchants.inflame.projectile.description",
      "Sets projectile targets ablaze for %d seconds.", data.level() == 1 ? 3 : 5);
    return translate(viewer, "enchantment.enchants.inflame.melee.description",
      "Sets melee targets ablaze for %d seconds.", data.level() * 4);
  }
  @Override protected double modifyOutgoingDamage(EnchantmentDamageContext context, double damage) {
    if (context.other() instanceof LivingEntity target)
      target.setFireTicks(resultingFireTicks(target.getFireTicks(), context.level(), context.projectile()));
    return damage;
  }
  public static int resultingFireTicks(int currentTicks, int level, boolean projectile) {
    int duration = projectile ? projectileFireTicks(level) : Math.max(0, level) * 4 * 20;
    return Math.max(currentTicks, duration);
  }
  public static int projectileFireTicks(int level) { return (level <= 1 ? 3 : 5) * 20; }
  private static boolean projectileWeapon(Material material) { return material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT; }
}
