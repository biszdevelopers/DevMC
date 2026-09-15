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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Revised table catalog, category mapping, applicability, and custom definitions. */
final class EnchantmentCatalog {
  private static final Map<String, EnchantmentCategory> CATEGORIES = buildCategories();

  private EnchantmentCatalog() {}

  static List<CustomEnchantment> customDefinitions() {
    return List.of(
      new LethalityEnchantment(), new PenetrationEnchantment(), new InflameEnchantment(), new KnockbackEnchantment(),
      new NimbleEnchantment(), new AcrobaticsEnchantment(), new WingedEnchantment(), new ImpactResistanceEnchantment()
    );
  }

  static EnchantmentCategory category(DevEnchantment enchantment) {
    return CATEGORIES.get(enchantment.id().toString());
  }

  static boolean offered(DevEnchantment enchantment) { return category(enchantment) != null; }

  /** Maximum levels copied from trueMC's enchantment catalog. */
  static int maximumLevel(DevEnchantment enchantment) {
    return MAX_LEVELS.getOrDefault(enchantment.id().toString(), 1);
  }

  /** The catalog entry a vanilla enchantment key converts into, if any. */
  static EnchantmentId vanillaTarget(String vanillaKey) {
    return VANILLA_TARGETS.get(vanillaKey);
  }

  /** Efficiency alone may occupy more than one socket on the same item. */
  static boolean repeatable(DevEnchantment enchantment) {
    String id = enchantment.id().toString();
    return id.equals("minecraft:efficiency") || id.equals("minecraft:dig_speed");
  }

  static boolean applicable(DevEnchantment enchantment, Material material) {
    EnchantmentCategory category = category(enchantment);
    if (category == null || !SocketLayouts.forMaterial(material).contains(category)) return false;
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

  /**
   * The pool entries that can still be applied to one socket without breaking a
   * rule: the socket must accept the enchantment for the item's material, the
   * enchantment may not already be present unless it is repeatable, and it may
   * not conflict with anything already on the item. An empty result is the only
   * case in which the table may present no offer for the socket.
   */
  static List<DevEnchantment> candidates(
    EnchantmentSlot slot,
    Material material,
    Iterable<DevEnchantment> existing,
    Collection<DevEnchantment> pool
  ) {
    ArrayList<DevEnchantment> result = new ArrayList<>();
    for (DevEnchantment candidate : pool) {
      if (!offered(candidate)) continue;
      if (!slot.accepts(candidate, material)) continue;
      if (!compatible(candidate, existing)) continue;
      result.add(candidate);
    }
    return List.copyOf(result);
  }

  /** Whether an enchantment may join the enchantments already on the item. */
  static boolean compatible(DevEnchantment candidate, Iterable<DevEnchantment> existing) {
    boolean present = false;
    for (DevEnchantment other : existing) {
      if (other.id().equals(candidate.id())) {
        present = true;
        continue;
      }
      if (conflicts(candidate, other)) return false;
    }
    return !present || repeatable(candidate);
  }

  static boolean conflicts(DevEnchantment left, DevEnchantment right) {
    String a = left.id().path(), b = right.id().path();
    if (a.equals(b)) return false;
    Set<String> pair = Set.of(a, b);
    if (pair.contains("silk_touch") && (pair.contains("fortune") || pair.contains("loot_bonus_blocks"))) return true;
    if (pair.contains("riptide") && (pair.contains("loyalty") || pair.contains("channeling"))) return true;
    if (left instanceof VanillaEnchantment lv && right instanceof VanillaEnchantment rv)
      return lv.bukkit().conflictsWith(rv.bukkit()) || rv.bukkit().conflictsWith(lv.bukkit());
    return false;
  }

  private static boolean isProjectileWeapon(String name) { return name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT"); }
  private static boolean isWeapon(String name) { return isProjectileWeapon(name) || name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_HOE") || name.endsWith("_SPEAR") || name.equals("MACE"); }
  private static boolean isTool(String name) { return name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE"); }
  private static boolean isArmor(String name) { return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS"); }

  private static Map<String, EnchantmentCategory> buildCategories() {
    LinkedHashMap<String, EnchantmentCategory> values = new LinkedHashMap<>();
    put(values, EnchantmentCategory.FATALITY, "enchants:lethality", "enchants:penetration");
    put(values, EnchantmentCategory.PROWESS, "enchants:inflame", "minecraft:looting", "minecraft:loot_bonus_mobs", "enchants:knockback", "enchants:nimble", "enchants:acrobatics", "minecraft:multishot");
    put(values, EnchantmentCategory.PROTECTION, "minecraft:protection", "minecraft:protection_environmental", "enchants:impact_resistance");
    put(values, EnchantmentCategory.MOBILITY, "minecraft:depth_strider", "minecraft:frost_walker", "minecraft:soul_speed", "minecraft:swift_sneak", "enchants:winged");
    put(values, EnchantmentCategory.TIDE, "minecraft:aqua_affinity", "minecraft:water_worker", "minecraft:respiration", "minecraft:oxygen", "minecraft:riptide", "minecraft:loyalty", "minecraft:channeling");
    put(values, EnchantmentCategory.HARVESTING, "minecraft:fortune", "minecraft:loot_bonus_blocks", "minecraft:silk_touch", "minecraft:efficiency", "minecraft:dig_speed");
    put(values, EnchantmentCategory.SUSTAINABILITY, "minecraft:mending", "minecraft:unbreaking", "minecraft:durability");
    return Map.copyOf(values);
  }

  private static final Map<String, Integer> MAX_LEVELS = buildMaximumLevels();

  private static Map<String, Integer> buildMaximumLevels() {
    LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
    putLevel(values, 5, "enchants:lethality");
    putLevel(values, 4, "enchants:penetration", "minecraft:piercing", "minecraft:breach");
    putLevel(values, 2, "enchants:inflame", "minecraft:fire_aspect", "minecraft:flame", "enchants:knockback", "minecraft:knockback", "minecraft:frost_walker");
    putLevel(values, 3, "minecraft:looting", "minecraft:loot_bonus_mobs", "enchants:nimble", "minecraft:quick_charge", "enchants:acrobatics", "minecraft:wind_burst", "minecraft:lunge", "minecraft:depth_strider", "minecraft:soul_speed", "minecraft:swift_sneak", "minecraft:respiration", "minecraft:loyalty", "minecraft:channeling", "minecraft:riptide", "minecraft:fortune", "minecraft:loot_bonus_blocks", "minecraft:unbreaking", "minecraft:durability");
    putLevel(values, 1, "minecraft:multishot", "enchants:winged", "minecraft:aqua_affinity", "minecraft:water_worker", "minecraft:silk_touch", "minecraft:mending");
    putLevel(values, 4, "minecraft:protection", "minecraft:protection_environmental", "enchants:impact_resistance");
    putLevel(values, 5, "minecraft:efficiency", "minecraft:dig_speed");
    return Map.copyOf(values);
  }

  private static final Map<String, EnchantmentId> VANILLA_TARGETS = buildVanillaTargets();

  private static Map<String, EnchantmentId> buildVanillaTargets() {
    LinkedHashMap<String, EnchantmentId> values = new LinkedHashMap<>();
    target(values, "enchants:lethality", "sharpness", "power", "density");
    target(values, "enchants:penetration", "piercing", "breach");
    target(values, "enchants:inflame", "fire_aspect", "flame");
    target(values, "minecraft:looting", "looting");
    target(values, "enchants:knockback", "knockback");
    target(values, "enchants:nimble", "quick_charge");
    target(values, "minecraft:multishot", "multishot");
    target(values, "enchants:acrobatics", "wind_burst", "lunge");
    target(values, "minecraft:protection", "protection");
    target(values, "minecraft:depth_strider", "depth_strider");
    target(values, "minecraft:frost_walker", "frost_walker");
    target(values, "minecraft:soul_speed", "soul_speed");
    target(values, "minecraft:swift_sneak", "swift_sneak");
    target(values, "minecraft:aqua_affinity", "aqua_affinity");
    target(values, "minecraft:respiration", "respiration");
    target(values, "minecraft:loyalty", "loyalty");
    target(values, "minecraft:channeling", "channeling");
    target(values, "minecraft:riptide", "riptide");
    target(values, "minecraft:efficiency", "efficiency");
    target(values, "minecraft:fortune", "fortune");
    target(values, "minecraft:silk_touch", "silk_touch");
    target(values, "minecraft:mending", "mending");
    target(values, "minecraft:unbreaking", "unbreaking");
    return Map.copyOf(values);
  }

  private static void target(Map<String, EnchantmentId> values, String id, String... keys) {
    int separator = id.indexOf(':');
    EnchantmentId target = EnchantmentId.of(id.substring(0, separator), id.substring(separator + 1));
    for (String key : keys) values.put(key, target);
  }

  private static void putLevel(Map<String, Integer> values, int level, String... ids) {
    for (String id : ids) values.put(id, level);
  }
  private static void put(Map<String, EnchantmentCategory> values, EnchantmentCategory category, String... ids) {
    for (String id : ids) values.put(id, category);
  }
}
