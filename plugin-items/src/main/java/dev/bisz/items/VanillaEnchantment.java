package dev.bisz.items;

import java.util.Objects;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

/** DevEnchantment adapter for a Minecraft-owned Bukkit enchantment. */
public final class VanillaEnchantment extends DevEnchantment {
  private final Enchantment bukkit;
  VanillaEnchantment(Enchantment bukkit) {
    super(EnchantmentId.of(bukkit.getKey().getNamespace(), bukkit.getKey().getKey()), EnchantmentProperties.builder().quality(qualityOf(bukkit)).build());
    this.bukkit = Objects.requireNonNull(bukkit, "bukkit");
  }
  public Enchantment bukkit() { return bukkit; }
  @Override public boolean vanilla() { return true; }
  @Override protected String renderName(Player viewer) {
    return ItemTranslations.minecraft(ItemTranslations.language(viewer), "enchantment." + id().namespace() + "." + id().path(), ItemTranslations.humanize(id().path()));
  }
  private static Quality qualityOf(Enchantment enchantment) {
    String key = enchantment.getKey().getKey();
    return key.equals("mending") || key.equals("binding_curse") || key.equals("vanishing_curse") ? Quality.RARE : Quality.COMMON;
  }
}
