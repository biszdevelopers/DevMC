package dev.bisz.enchants;

import dev.bisz.items.Ability;
import dev.bisz.items.AbilityUsageMethod;
import dev.bisz.items.DevEnchantment;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.Quality;
import org.bukkit.util.Vector;

/** Fishing-rod-only display and movement behavior for vanilla Riptide. */
final class RiptideFishingRodSpecialty {
  static final EnchantmentId RIPTIDE_ID = EnchantmentId.of("minecraft", "riptide");
  static final Ability GRAPPLE = Ability.builder("ability.enchants.grapple")
    .name("Grapple")
    .description("Pulls you toward the fishing hook while in water or rain.")
    .quality(Quality.COMMON)
    .usage(AbilityUsageMethod.REEL_IN)
    .cooldownSeconds(0D)
    .build();

  private RiptideFishingRodSpecialty() {}

  static boolean isRiptide(DevEnchantment enchantment) {
    return enchantment.id().equals(RIPTIDE_ID);
  }

  static Vector pullVelocity(Vector displacement, int level) {
    if (displacement.lengthSquared() == 0D || level <= 0) return new Vector();
    double speed = .75D * (Math.min(3, level) + 1D);
    Vector velocity = displacement.clone().normalize().multiply(speed);
    velocity.setY(velocity.getY() + .15D);
    return velocity;
  }
}
