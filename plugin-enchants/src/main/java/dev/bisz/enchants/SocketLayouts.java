package dev.bisz.enchants;

import static dev.bisz.enchants.EnchantmentCategory.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/** Exact material allowlist and ordered typed socket layouts. */
final class SocketLayouts {
  private SocketLayouts() {}

  static List<EnchantmentCategory> forMaterial(Material material) {
    String name = material.name();
    if (name.startsWith("WOODEN_")) return List.of();
    return switch (name) {
      case "BOOK", "ENCHANTED_BOOK" -> list(UNIVERSAL);
      case "BOW" -> list(FATALITY, PROWESS);
      case "CROSSBOW" -> list(FATALITY, PROWESS);
      case "TRIDENT" -> list(FATALITY, PROWESS, TIDE, TIDE);
      case "FISHING_ROD" -> list(TIDE, TIDE, HARVESTING, HARVESTING, HARVESTING);
      case "TURTLE_HELMET" -> list(TIDE);
      case "STONE_SWORD" -> list(FATALITY);
      case "STONE_AXE" -> list(FATALITY, HARVESTING);
      case "STONE_PICKAXE", "STONE_SHOVEL", "STONE_HOE" -> list(HARVESTING);
      case "GOLDEN_HELMET" -> list(PROTECTION, PROTECTION, TIDE);
      case "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS" -> list(PROTECTION, PROTECTION);
      case "GOLDEN_BOOTS" -> list(PROTECTION, PROTECTION, MOBILITY, MOBILITY);
      case "GOLDEN_SWORD" -> list(FATALITY, PROWESS, PROWESS, SUSTAINABILITY, SUSTAINABILITY);
      case "GOLDEN_HOE" -> list(HARVESTING, SUSTAINABILITY, SUSTAINABILITY);
      case "GOLDEN_AXE" -> list(FATALITY, PROWESS, HARVESTING, HARVESTING, SUSTAINABILITY, SUSTAINABILITY);
      case "GOLDEN_PICKAXE" -> list(HARVESTING, HARVESTING, HARVESTING, SUSTAINABILITY, SUSTAINABILITY);
      case "GOLDEN_SHOVEL" -> list(HARVESTING, SUSTAINABILITY, SUSTAINABILITY, SUSTAINABILITY);
      case "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS",
           "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
           "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS" -> list(PROTECTION);
      case "IRON_BOOTS", "NETHERITE_BOOTS" -> list(PROTECTION, MOBILITY);
      case "IRON_SWORD" -> list(FATALITY, PROWESS);
      case "IRON_AXE" -> list(FATALITY, HARVESTING, HARVESTING);
      case "IRON_PICKAXE", "IRON_SHOVEL" -> list(HARVESTING, HARVESTING);
      case "IRON_HOE" -> list(HARVESTING);
      case "DIAMOND_SWORD" -> list(FATALITY, PROWESS, SUSTAINABILITY);
      case "DIAMOND_AXE" -> list(FATALITY, HARVESTING, HARVESTING, SUSTAINABILITY);
      case "DIAMOND_PICKAXE", "DIAMOND_SHOVEL" -> list(HARVESTING, HARVESTING, SUSTAINABILITY);
      case "DIAMOND_HOE" -> list(HARVESTING, SUSTAINABILITY);
      case "NETHERITE_SWORD" -> list(FATALITY, PROWESS, PROWESS);
      case "NETHERITE_AXE" -> list(FATALITY, PROWESS, HARVESTING);
      case "NETHERITE_HOE" -> list(FATALITY, FATALITY, FATALITY, PROWESS, PROWESS, PROWESS, HARVESTING);
      case "NETHERITE_SHOVEL" -> list(HARVESTING);
      case "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS",
           "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS" -> list(PROTECTION);
      default -> future(name);
    };
  }

  private static List<EnchantmentCategory> future(String name) {
    if (name.equals("MACE") || name.matches("(STONE|COPPER|GOLDEN|IRON|DIAMOND|NETHERITE)_SPEAR")) return list(FATALITY, PROWESS);
    if (name.startsWith("COPPER_")) {
      if (name.endsWith("BOOTS")) return list(PROTECTION, MOBILITY);
      if (name.endsWith("HELMET") || name.endsWith("CHESTPLATE") || name.endsWith("LEGGINGS")) return list(PROTECTION);
      if (name.endsWith("SWORD")) return list(FATALITY, PROWESS);
      if (name.endsWith("AXE")) return list(FATALITY, HARVESTING, HARVESTING);
      if (name.endsWith("PICKAXE") || name.endsWith("SHOVEL")) return list(HARVESTING, HARVESTING);
      if (name.endsWith("HOE")) return list(HARVESTING);
    }
    return List.of();
  }

  private static List<EnchantmentCategory> list(EnchantmentCategory... categories) {
    ArrayList<EnchantmentCategory> sorted = new ArrayList<>(List.of(categories));
    sorted.sort(java.util.Comparator.comparingInt(Enum::ordinal));
    return List.copyOf(sorted);
  }
}
