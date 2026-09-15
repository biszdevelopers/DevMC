package dev.bisz.enchants;

import dev.bisz.players.locales.Locale;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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

  private static final Map<String, EnchantmentCategory> KEYS = new HashMap<>(Map.ofEntries(
    Map.entry("m_damage", FATALITY),
    Map.entry("m_util", PROWESS),
    Map.entry("protection", PROTECTION),
    Map.entry("mobility", MOBILITY),
    Map.entry("water", TIDE),
    Map.entry("tide", TIDE),
    Map.entry("efficiency", HARVESTING),
    Map.entry("harvesting", HARVESTING),
    Map.entry("sustainability", SUSTAINABILITY),
    Map.entry("universal", UNIVERSAL)
  ));

  private final String icon;
  private final String color;

  EnchantmentCategory(String icon, String color) {
    this.icon = icon;
    this.color = color;
  }

  /** Loads the config key to category mapping from {@code categories.yml}. */
  static void loadKeys(FileConfiguration categories) {
    ConfigurationSection root = categories.getConfigurationSection("categories");
    if (root == null) return;
    for (String key : root.getKeys(false)) {
      String enumName = root.getString(key + ".enum");
      if (enumName == null) continue;
      try {
        KEYS.put(key.toLowerCase(java.util.Locale.ROOT), valueOf(enumName.toUpperCase(java.util.Locale.ROOT)));
      } catch (IllegalArgumentException ignored) {
        // Unknown enum name falls back to the built-in mapping.
      }
    }
  }

  /** Resolves a socket layout category key, or {@code null} when unknown. */
  static EnchantmentCategory fromKey(String key) {
    return key == null ? null : KEYS.get(key.toLowerCase(java.util.Locale.ROOT));
  }

  public String icon() { return icon; }
  public String color() { return color; }
  public String localeKey() { return "enchants.category." + name().toLowerCase(java.util.Locale.ROOT); }
  public String displayName(Player viewer) {
    return viewer == null ? Locale.get("en_us", localeKey()) : Locale.get(viewer, localeKey());
  }
}
