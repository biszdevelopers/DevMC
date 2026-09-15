package dev.bisz.enchants;

import dev.bisz.players.locales.Locale;
import org.bukkit.entity.Player;

/** Typed socket families plus the universal socket used by books. */
public enum EnchantmentCategory {
  FATALITY("⚔", "§c"),
  PROWESS("✦", "§d"),
  PROTECTION("⛨", "§a"),
  MOBILITY("☁", "§b"),
  TIDE("☀", "§9"),
  HARVESTING("⛏", "§6"),
  SUSTAINABILITY("♻", "§e"),
  UNIVERSAL("☢", "§f");

  private final String icon;
  private final String color;

  EnchantmentCategory(String icon, String color) {
    this.icon = icon;
    this.color = color;
  }

  public String icon() { return icon; }
  public String color() { return color; }
  public String localeKey() { return "enchants.category." + name().toLowerCase(java.util.Locale.ROOT); }
  public String displayName(Player viewer) {
    return viewer == null ? Locale.get("en_us", localeKey()) : Locale.get(viewer, localeKey());
  }
}
