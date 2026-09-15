package dev.bisz.items;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/** Singleton definition for a vanilla or plugin-defined enchantment. */
public abstract class DevEnchantment {
  private final EnchantmentId id;
  private final EnchantmentProperties properties;

  protected DevEnchantment(EnchantmentId id, EnchantmentProperties properties) {
    this.id = Objects.requireNonNull(id, "id");
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  public final EnchantmentId id() { return id; }
  public final EnchantmentProperties properties() { return properties; }
  public abstract boolean vanilla();

  protected String renderName(Player viewer) {
    return translate(viewer, "enchantment." + id.namespace() + "." + id.path(), ItemTranslations.humanize(id.path()));
  }
  /** Renders one complete lore line; custom enchantments may override its color treatment. */
  protected String renderLore(Player viewer, int level) {
    String color = properties.quality() == Quality.COMMON ? "§7" : properties.quality().colorCode();
    return color + renderName(viewer) + " " + RomanNumerals.format(level);
  }
  /**
   * Returns the localized description for one applied enchantment. The complete
   * data object is supplied so implementations can describe future perk metadata.
   */
  protected String renderDescription(Player viewer, EnchantmentData data) {
    return translate(
      viewer,
      "enchantment." + id.namespace() + "." + id.path() + ".description",
      "A magical property applied to this item."
    );
  }
  protected void onHandTick(DevItemStack stack, int level, Player holder, EquipmentSlot hand) {}
  protected void onInventoryTick(DevItemStack stack, int level, Player holder, int slot) {}
  protected double modifyOutgoingDamage(EnchantmentDamageContext context, double damage) { return damage; }
  protected double modifyIncomingDamage(EnchantmentDamageContext context, double damage) { return damage; }

  /** Returns this enchantment's localized display name. */
  public final String displayName(Player viewer) { return renderName(viewer); }
  /** Returns this enchantment's normal localized lore line. */
  public final String displayLore(Player viewer, int level) { return renderLore(viewer, level); }
  /** Returns this enchantment's localized description for the supplied item data. */
  public String displayDescription(Player viewer, EnchantmentData data) {
    return renderDescription(viewer, Objects.requireNonNull(data, "data"));
  }
  /** Allows descriptions to omit mechanics that do not apply to a material. */
  public String displayDescription(Player viewer, EnchantmentData data, Material material) {
    Objects.requireNonNull(material, "material");
    return displayDescription(viewer, data);
  }
  final String name(Player viewer) { return displayName(viewer); }
  final String lore(Player viewer, int level) { return displayLore(viewer, level); }
  final void handTick(DevItemStack stack, int level, Player holder, EquipmentSlot hand) { onHandTick(stack, level, holder, hand); }
  final void inventoryTick(DevItemStack stack, int level, Player holder, int slot) { onInventoryTick(stack, level, holder, slot); }
  final double outgoingDamage(EnchantmentDamageContext context, double damage) { return modifyOutgoingDamage(context, damage); }
  final double incomingDamage(EnchantmentDamageContext context, double damage) { return modifyIncomingDamage(context, damage); }

  protected final String translate(Player viewer, String key, String fallback, Object... arguments) {
    return ItemTranslations.translate(ItemTranslations.language(viewer), key, fallback, arguments);
  }
}
