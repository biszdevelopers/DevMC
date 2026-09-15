package dev.bisz.enchants.items;

import dev.bisz.chat.ChatUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Projectile piercing and mace protection penetration definition. */
public final class PenetrationEnchantment extends EnchantsEnchantment {
  public PenetrationEnchantment() { super("penetration"); }
  @Override protected String fallbackDescription(int level) {
    return "Projectiles pierce %d target%s. Maces reduce Protection by %d%% (coming in the protection update).";
  }
  @Override protected Object[] descriptionArguments(int level) { return new Object[] { level, ChatUtils.plural(level), level * 15 }; }
  @Override public String displayDescription(Player viewer, dev.bisz.items.EnchantmentData data, Material material) {
    if (material.name().equals("MACE")) return translate(viewer, "enchantment.enchants.penetration.mace.description",
      "Reduces target Protection by %d%% (coming in the protection update).", data.level() * 15);
    return translate(viewer, "enchantment.enchants.penetration.projectile.description",
      "Projectiles pierce %d target%s.", data.level(), ChatUtils.plural(data.level()));
  }
}
