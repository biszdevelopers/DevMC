package dev.bisz.enchants.items;

import dev.bisz.items.EnchantmentDamageContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Unified melee and projectile knockback enchantment. */
public final class KnockbackEnchantment extends EnchantsEnchantment {
  public KnockbackEnchantment() { super("knockback"); }
  @Override protected String fallbackDescription(int level) { return "Increases melee and projectile knockback by %d."; }
  @Override protected Object[] descriptionArguments(int level) { return new Object[] { level }; }
  @Override public String displayDescription(Player viewer, dev.bisz.items.EnchantmentData data, Material material) {
    String mode = projectileWeapon(material) ? "projectile" : "melee";
    return translate(viewer, "enchantment.enchants.knockback." + mode + ".description",
      "Increases " + mode + " knockback by %d.", data.level());
  }
  @Override protected double modifyOutgoingDamage(EnchantmentDamageContext context, double damage) {
    if (context.other() instanceof LivingEntity target) {
      Vector push = target.getLocation().toVector().subtract(context.holder().getLocation().toVector())
        .setY(0).normalize().multiply(.35D * context.level());
      target.setVelocity(target.getVelocity().add(push).setY(Math.max(target.getVelocity().getY(), .1D * context.level())));
    }
    return damage;
  }
  private static boolean projectileWeapon(Material material) { return material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT; }
}
