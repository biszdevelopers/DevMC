package dev.bisz.enchants.items;

import dev.bisz.chat.ChatUtils;
import dev.bisz.items.EnchantmentDamageContext;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** Projectile piercing and armor-bypassing penetration definition. */
public final class PenetrationEnchantment extends EnchantsEnchantment {
  public PenetrationEnchantment() { super("penetration"); }
  @Override protected String fallbackDescription(int level) {
    return "Projectiles pierce %d target%s. Attacks bypass %d%% of the target's armor.";
  }
  @Override protected Object[] descriptionArguments(int level) { return new Object[] { level, ChatUtils.plural(level), level * 15 }; }
  @Override public String displayDescription(Player viewer, dev.bisz.items.EnchantmentData data, Material material) {
    if (material.name().equals("MACE")) return translate(viewer, "enchantment.enchants.penetration.mace.description",
      "Reduces target armor by %d%%.", data.level() * 15);
    return translate(viewer, "enchantment.enchants.penetration.projectile.description",
      "Projectiles pierce %d target%s.", data.level(), ChatUtils.plural(data.level()));
  }

  /**
   * Adds back a portion of the target's armor reduction, matching trueMC's
   * breach effect for both melee and ranged hits.
   */
  @SuppressWarnings("deprecation")
  @Override protected double modifyOutgoingDamage(EnchantmentDamageContext context, double damage) {
    if (!(context.event() instanceof EntityDamageByEntityEvent event)) return damage;
    if (!event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) return damage;
    double reduction = Math.max(0D, -event.getDamage(EntityDamageEvent.DamageModifier.ARMOR));
    return damage + reduction * (.15D * context.level());
  }
}
