package dev.bisz.items;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/** Context supplied to custom enchantment damage modifiers. */
public record EnchantmentDamageContext(
  EntityDamageEvent event,
  Player holder,
  Entity other,
  DevItemStack stack,
  int level,
  boolean incoming,
  boolean projectile
) {}
