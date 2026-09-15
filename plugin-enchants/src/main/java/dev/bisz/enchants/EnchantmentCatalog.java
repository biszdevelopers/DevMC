package dev.bisz.enchants;

import dev.bisz.items.CustomEnchantment;
import dev.bisz.items.DevEnchantment;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.VanillaEnchantment;
import dev.bisz.enchants.items.AcrobaticsEnchantment;
import dev.bisz.enchants.items.ImpactResistanceEnchantment;
import dev.bisz.enchants.items.InflameEnchantment;
import dev.bisz.enchants.items.KnockbackEnchantment;
import dev.bisz.enchants.items.LethalityEnchantment;
import dev.bisz.enchants.items.NimbleEnchantment;
import dev.bisz.enchants.items.PenetrationEnchantment;
import dev.bisz.enchants.items.WingedEnchantment;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

/** Config-driven catalog: category mapping, maximum levels, and applicability. */
final class EnchantmentCatalog {
  /** Enchantments implemented by this plugin rather than Minecraft itself. */
  static final Set<String> CUSTOM_KEYS = Set.of(
    "lethality", "penetration", "inflame", "knockback",
    "nimble", "acrobatics", "winged", "impact_resistance"
  );

  private static final String[] TIER_PREFIXES = {
    "wooden_", "stone_", "copper_", "golden_", "iron_",
    "diamond_", "netherite_", "leather_", "chainmail_"
  };
  private static final String[] NON_TIER_PREFIXES = {"fishing_", "turtle_"};

  private static Map<EnchantmentId, EnchantmentCategory> categories = Map.of();
  private static Map<EnchantmentId, Integer> maxLevels = Map.of();
  private static Map<EnchantmentId, Set<String>> restrictedItems = Map.of();
  private static Map<EnchantmentId, List<String>> vanillaMappings = Map.of();
  private static Map<String, EnchantmentId> vanillaTargets = Map.of();

  private EnchantmentCatalog() {}

  static List<CustomEnchantment> customDefinitions() {
    return List.of(
      new LethalityEnchantment(), new PenetrationEnchantment(), new InflameEnchantment(), new KnockbackEnchantment(),
      new NimbleEnchantment(), new AcrobaticsEnchantment(), new WingedEnchantment(), new ImpactResistanceEnchantment()
    );
  }

  /** Loads enchantment metadata from {@code enchantments.yml}. */
  static void load(FileConfiguration enchantments) {
    Map<EnchantmentId, EnchantmentCategory> loadedCategories = new HashMap<>();
    Map<EnchantmentId, Integer> loadedLevels = new HashMap<>();
    Map<EnchantmentId, Set<String>> loadedItems = new HashMap<>();
    Map<EnchantmentId, List<String>> loadedVanilla = new HashMap<>();
    ConfigurationSection root = enchantments.getConfigurationSection("enchantments");
    if (root != null) {
      for (String key : root.getKeys(false)) {
        EnchantmentCategory category = EnchantmentCategory.fromKey(root.getString(key + ".category"));
        if (category == null) continue;
        EnchantmentId id = resolveId(key);
        loadedCategories.put(id, category);
        loadedLevels.put(id, Math.max(1, root.getInt(key + ".max-level", 1)));
        loadedItems.put(id, new HashSet<>(root.getStringList(key + ".items")));
        loadedVanilla.put(id, readVanilla(root, key));
      }
    }
    categories = Map.copyOf(loadedCategories);
    maxLevels = Map.copyOf(loadedLevels);
    restrictedItems = Map.copyOf(loadedItems);
    vanillaMappings = Map.copyOf(loadedVanilla);
    Map<String, EnchantmentId> targets = new HashMap<>();
    loadedVanilla.forEach((id, keys) -> keys.forEach(key -> targets.putIfAbsent(key, id)));
    vanillaTargets = Map.copyOf(targets);
  }

  static EnchantmentCategory category(DevEnchantment enchantment) {
    return categories.get(enchantment.id());
  }

  static boolean offered(DevEnchantment enchantment) { return category(enchantment) != null; }

  static int maximumLevel(DevEnchantment enchantment) {
    return maxLevels.getOrDefault(enchantment.id(), 1);
  }

  /** The vanilla enchantment keys this catalog entry replaces, if any. */
  static List<String> vanillaKeys(DevEnchantment enchantment) {
    return vanillaMappings.getOrDefault(enchantment.id(), List.of());
  }

  /** The catalog entry a vanilla enchantment key converts into, if any. */
  static EnchantmentId vanillaTarget(String vanillaKey) {
    return vanillaTargets.get(vanillaKey);
  }

  /** Efficiency alone may occupy more than one socket on the same item. */
  static boolean repeatable(DevEnchantment enchantment) {
    String path = enchantment.id().path();
    return path.equals("efficiency") || path.equals("dig_speed");
  }

  static boolean applicable(DevEnchantment enchantment, Material material) {
    EnchantmentCategory category = category(enchantment);
    if (category == null || !SocketLayouts.forMaterial(material).contains(category)) return false;
    Set<String> restricted = restrictedItems.get(enchantment.id());
    if (restricted != null && !restricted.isEmpty()) {
      return restricted.contains(baseItem(material.name().toLowerCase(java.util.Locale.ROOT)));
    }
    return categoryApplicable(enchantment, material);
  }

  static boolean conflicts(DevEnchantment left, DevEnchantment right) {
    String a = left.id().path(), b = right.id().path();
    Set<String> pair = Set.of(a, b);
    if (pair.contains("silk_touch") && (pair.contains("fortune") || pair.contains("loot_bonus_blocks"))) return true;
    if (pair.contains("riptide") && (pair.contains("loyalty") || pair.contains("channeling"))) return true;
    if (left instanceof VanillaEnchantment lv && right instanceof VanillaEnchantment rv)
      return lv.bukkit().conflictsWith(rv.bukkit()) || rv.bukkit().conflictsWith(lv.bukkit());
    return false;
  }

  private static boolean categoryApplicable(DevEnchantment enchantment, Material material) {
    EnchantmentCategory category = category(enchantment);
    String id = enchantment.id().toString();
    String name = material.name();
    if (id.endsWith(":aqua_affinity") || id.endsWith(":water_worker") || id.endsWith(":respiration") || id.endsWith(":oxygen")) return name.endsWith("HELMET");
    if (id.equals("enchants:penetration")) return isProjectileWeapon(name) || name.equals("MACE");
    if (id.equals("minecraft:riptide") || id.equals("minecraft:loyalty") || id.equals("minecraft:channeling")) return name.equals("TRIDENT") || name.equals("FISHING_ROD");
    if (id.equals("minecraft:multishot")) return name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT");
    if (id.equals("enchants:acrobatics")) return name.equals("MACE") || name.endsWith("_SPEAR");
    if (id.equals("enchants:winged")) return name.endsWith("BOOTS");
    if (enchantment instanceof VanillaEnchantment vanilla) {
      if (name.equals("FISHING_ROD") && (category == EnchantmentCategory.TIDE || category == EnchantmentCategory.HARVESTING)) return true;
      return vanilla.bukkit().canEnchantItem(new ItemStack(material));
    }
    if (category == EnchantmentCategory.MOBILITY) return name.endsWith("BOOTS");
    if (category == EnchantmentCategory.HARVESTING) return isTool(name) || name.equals("FISHING_ROD");
    if (category == EnchantmentCategory.PROTECTION) return isArmor(name);
    if (category == EnchantmentCategory.SUSTAINABILITY) return true;
    return isWeapon(name);
  }

  private static EnchantmentId resolveId(String key) {
    return EnchantmentId.of(CUSTOM_KEYS.contains(key) ? "enchants" : "minecraft", key);
  }

  private static String baseItem(String materialKey) {
    String base = materialKey;
    for (String prefix : TIER_PREFIXES) {
      if (base.startsWith(prefix)) { base = base.substring(prefix.length()); break; }
    }
    for (String prefix : NON_TIER_PREFIXES) {
      if (base.startsWith(prefix)) { base = base.substring(prefix.length()); break; }
    }
    return base;
  }

  private static List<String> readVanilla(ConfigurationSection root, String key) {
    Object value = root.get(key + ".vanilla");
    if (value == null) return List.of();
    if (value instanceof List<?>) return List.copyOf(root.getStringList(key + ".vanilla"));
    return List.of(String.valueOf(value));
  }

  private static boolean isProjectileWeapon(String name) { return name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT"); }
  private static boolean isWeapon(String name) { return isProjectileWeapon(name) || name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_HOE") || name.endsWith("_SPEAR") || name.equals("MACE"); }
  private static boolean isTool(String name) { return name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE"); }
  private static boolean isArmor(String name) { return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS"); }
}
