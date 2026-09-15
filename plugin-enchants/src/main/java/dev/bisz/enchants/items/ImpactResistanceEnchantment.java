package dev.bisz.enchants.items;

import dev.bisz.items.EnchantmentDamageContext;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** Reduces fall, explosion, mace, and spear impact damage. */
public final class ImpactResistanceEnchantment extends EnchantsEnchantment {
  public ImpactResistanceEnchantment() { super("impact_resistance"); }
  @Override protected String fallbackDescription(int level) {
    return "Reduces fall, explosion, mace, and spear damage by %d%%.";
  }
  @Override protected Object[] descriptionArguments(int level) { return new Object[] { level * 10 }; }
  @Override protected double modifyIncomingDamage(EnchantmentDamageContext context, double damage) {
    double scale = Math.max(0D, 1D - .1D * context.level());
    EntityDamageEvent event = context.event();
    return switch (event.getCause()) {
      case FALL, BLOCK_EXPLOSION, ENTITY_EXPLOSION -> damage * scale;
      default -> event instanceof EntityDamageByEntityEvent by && isHeavyImpact(by.getDamager())
        ? damage * scale : damage;
    };
  }

  private static boolean isHeavyImpact(Entity damager) {
    if (!(damager instanceof Player player)) return false;
    Material type = player.getInventory().getItemInMainHand().getType();
    return type.name().equals("MACE") || type.name().endsWith("_SPEAR");
  }
}
