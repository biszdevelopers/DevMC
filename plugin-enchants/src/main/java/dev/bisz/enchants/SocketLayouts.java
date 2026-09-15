package dev.bisz.enchants;

import static dev.bisz.enchants.EnchantmentCategory.UNIVERSAL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Config-driven material allowlist and ordered typed socket layouts. */
final class SocketLayouts {
  private static Map<String, List<EnchantmentCategory>> layouts = Map.of();

  private SocketLayouts() {}

  /** Loads {@code sockets.yml}, resolving each configured category key. */
  static void load(FileConfiguration sockets, FileConfiguration categories) {
    EnchantmentCategory.loadKeys(categories);
    Map<String, List<EnchantmentCategory>> loaded = new HashMap<>();
    ConfigurationSection root = sockets.getConfigurationSection("sockets");
    if (root != null) {
      for (String key : root.getKeys(false)) {
        ArrayList<EnchantmentCategory> resolved = new ArrayList<>();
        for (String raw : root.getStringList(key)) {
          EnchantmentCategory category = EnchantmentCategory.fromKey(raw);
          if (category != null) resolved.add(category);
        }
        // Every socketable item has one implicit Sustainability socket.
        if (!resolved.isEmpty()) {
          resolved.add(EnchantmentCategory.SUSTAINABILITY);
        }
        loaded.put(key.toLowerCase(Locale.ROOT), List.copyOf(resolved));
      }
    }
    layouts = Map.copyOf(loaded);
  }

  static List<EnchantmentCategory> forMaterial(Material material) {
    String name = material.name().toLowerCase(Locale.ROOT);
    if (name.equals("book") || name.equals("enchanted_book")) return list(UNIVERSAL);
    List<EnchantmentCategory> layout = layouts.get(name);
    if (layout == null || layout.isEmpty()) return List.of();
    return list(layout.toArray(new EnchantmentCategory[0]));
  }

  private static List<EnchantmentCategory> list(EnchantmentCategory... categories) {
    ArrayList<EnchantmentCategory> sorted = new ArrayList<>(List.of(categories));
    sorted.sort(Comparator.comparingInt(Enum::ordinal));
    return List.copyOf(sorted);
  }
}
