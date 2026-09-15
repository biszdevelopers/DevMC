package dev.bisz.enchants.items;

import dev.bisz.items.Ability;
import dev.bisz.items.AbilityUsageMethod;
import dev.bisz.items.EnchantmentDamageContext;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.Quality;
import org.bukkit.event.entity.EntityDamageEvent;

/** Fall protection and Double Jump ability definition. */
public final class WingedEnchantment extends EnchantsEnchantment {
  public static final EnchantmentId ID = EnchantmentId.of("enchants", "winged");
  public static final Ability DOUBLE_JUMP = Ability.builder("ability.enchants.double_jump")
    .name("Double Jump")
    .description("Allows you to jump in the air.")
    .quality(Quality.COMMON)
    .usage(AbilityUsageMethod.DOUBLE_JUMP)
    .cooldownSeconds(3D)
    .build();

  public WingedEnchantment() { super("winged"); }
  @Override protected String fallbackDescription(int level) {
    return "Halves fall damage, grants one double jump while airborne, and prevents fall damage during its cooldown.";
  }
  @Override protected double modifyIncomingDamage(EnchantmentDamageContext context, double damage) {
    return context.event().getCause() == EntityDamageEvent.DamageCause.FALL ? damage * .5D : damage;
  }
}
