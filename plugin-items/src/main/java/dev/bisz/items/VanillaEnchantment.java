package dev.bisz.items;

import java.util.Objects;
import org.bukkit.Material;
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
  @Override protected String renderDescription(Player viewer, EnchantmentData data) {
    return translate(viewer, "enchantment.minecraft." + id().path() + ".description", description(id().path()));
  }
  @Override public String displayDescription(Player viewer, EnchantmentData data, Material material) {
    Objects.requireNonNull(data, "data");
    Objects.requireNonNull(material, "material");
    if (id().path().equals("riptide") && material == Material.FISHING_ROD)
      return translate(viewer, "enchantment.minecraft.riptide.fishing_rod.description",
        "Turns the fishing rod into a grappling hook.");
    return super.displayDescription(viewer, data, material);
  }
  private static String description(String key) {
    return switch (key) {
      case "aqua_affinity", "water_worker" -> "Increases underwater mining speed.";
      case "bane_of_arthropods", "damage_arthropods" -> "Deals extra damage to arthropods and briefly slows them.";
      case "binding_curse" -> "Prevents equipped armor from being removed.";
      case "blast_protection", "protection_explosions" -> "Reduces explosion damage and knockback.";
      case "channeling" -> "Calls lightning when a thrown trident strikes during a thunderstorm.";
      case "depth_strider" -> "Increases underwater movement speed.";
      case "efficiency", "dig_speed" -> "Increases mining speed.";
      case "feather_falling", "protection_fall" -> "Reduces fall damage.";
      case "fire_aspect" -> "Sets melee targets on fire.";
      case "fire_protection", "protection_fire" -> "Reduces fire damage and burn duration.";
      case "flame", "arrow_fire" -> "Sets arrows and their targets on fire.";
      case "fortune", "loot_bonus_blocks" -> "Increases drops from certain blocks.";
      case "frost_walker" -> "Temporarily freezes water beneath the wearer.";
      case "impaling" -> "Deals extra damage to aquatic targets.";
      case "infinity", "arrow_infinite" -> "Allows normal arrows to be fired without consuming them.";
      case "knockback" -> "Increases melee knockback.";
      case "looting", "loot_bonus_mobs" -> "Increases drops from defeated creatures.";
      case "loyalty" -> "Returns a thrown trident to its owner.";
      case "luck_of_the_sea", "luck" -> "Improves the quality of fishing loot.";
      case "lure" -> "Reduces the wait before fish bite.";
      case "mending" -> "Uses collected experience to repair the item.";
      case "multishot" -> "Fires three projectiles at once while consuming one.";
      case "piercing" -> "Allows crossbow arrows to pass through targets.";
      case "power", "arrow_damage" -> "Increases arrow damage.";
      case "projectile_protection", "protection_projectile" -> "Reduces projectile damage.";
      case "protection", "protection_environmental" -> "Reduces most incoming damage.";
      case "punch", "arrow_knockback" -> "Increases arrow knockback.";
      case "quick_charge" -> "Reduces crossbow loading time.";
      case "respiration", "oxygen" -> "Extends underwater breathing time.";
      case "riptide" -> "Launches the wielder with a thrown trident in water or rain.";
      case "sharpness", "damage_all" -> "Increases melee damage.";
      case "silk_touch" -> "Causes certain blocks to drop themselves.";
      case "smite", "damage_undead" -> "Deals extra damage to undead targets.";
      case "soul_speed" -> "Increases movement speed on soul blocks.";
      case "sweeping_edge", "sweeping" -> "Increases sweep-attack damage.";
      case "swift_sneak" -> "Increases movement speed while sneaking.";
      case "thorns" -> "Damages attackers who strike the wearer.";
      case "unbreaking", "durability" -> "Reduces the chance that durability is consumed.";
      case "vanishing_curse" -> "Causes the item to disappear when its owner dies.";
      default -> "Adds a magical property to this item.";
    };
  }
  private static Quality qualityOf(Enchantment enchantment) {
    String key = enchantment.getKey().getKey();
    return key.equals("mending") || key.equals("binding_curse") || key.equals("vanishing_curse") ? Quality.RARE : Quality.COMMON;
  }
}
