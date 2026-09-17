package dev.bisz.items;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Vanilla override base for items which retain a repairable broken state. */
public abstract class OverrideDamageableVanillaItem extends OverrideVanillaItem {
  static final String BROKEN_KEY = "broken";

  protected OverrideDamageableVanillaItem(Material material, ItemProperties properties) {
    this(material, properties, false);
  }

  /** Only shared non-damageable specializations (such as socket books) may opt out. */
  protected OverrideDamageableVanillaItem(Material material, ItemProperties properties, boolean permitNonDamageable) {
    super(material, properties);
    if (
      !permitNonDamageable &&
      Bukkit.getServer() != null &&
      material.getMaxDurability() <= 0
    ) throw new IllegalArgumentException("Damageable override requires durability: " + material);
  }

  public final boolean broken(DevItemStack stack) {
    ItemMeta meta = stack.bukkitStack().getItemMeta();
    return meta != null && meta.getPersistentDataContainer().has(brokenKey(), PersistentDataType.BYTE);
  }

  public final int remainingDurability(DevItemStack stack) {
    if (material().getMaxDurability() <= 0) return 0;
    if (broken(stack)) return 0;
    ItemMeta meta = stack.bukkitStack().getItemMeta();
    int damage = meta instanceof Damageable damageable ? damageable.getDamage() : 0;
    return Math.max(0, material().getMaxDurability() - Math.max(0, damage));
  }

  final void broken(DevItemStack stack, boolean value) {
    ItemMeta meta = stack.bukkitStack().getItemMeta();
    if (meta == null) return;
    if (value) meta.getPersistentDataContainer().set(brokenKey(), PersistentDataType.BYTE, (byte) 1);
    else meta.getPersistentDataContainer().remove(brokenKey());
    stack.bukkitStack().setItemMeta(meta);
    stack.invalidateRender();
  }

  final void prepareMend(DevItemStack stack) {
    if (!broken(stack)) return;
    ItemMeta meta = stack.bukkitStack().getItemMeta();
    if (meta instanceof Damageable damageable) damageable.setDamage(material().getMaxDurability());
    if (meta != null) stack.bukkitStack().setItemMeta(meta);
    broken(stack, false);
  }

  @Override protected final List<String> renderStatusLore(DevItemStack stack, Player viewer) {
    int maximum = material().getMaxDurability();
    if (maximum <= 0) return List.of();
    int remaining = remainingDurability(stack);
    String amountColor = remaining >= maximum * .5D ? "§7" : remaining >= maximum * .25D ? "§e" : remaining >= maximum * .10D ? "§6" : "§c";
    String line = "§7⌛ " + amountColor + format(remaining) + "§8/§7" + format(maximum);
    return broken(stack) ? List.of(line + " §c§lBROKEN!") : List.of(line);
  }

  static String format(int value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
  private static NamespacedKey brokenKey() { return new NamespacedKey(ItemsPlugin.instance(), BROKEN_KEY); }
}
