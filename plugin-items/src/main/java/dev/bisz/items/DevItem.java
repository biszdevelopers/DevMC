/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 */
package dev.bisz.items;

import dev.bisz.items.DevItemStack;
import dev.bisz.items.ItemId;
import dev.bisz.items.ItemProperties;
import dev.bisz.items.ItemTranslations;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public abstract class DevItem {

  private final ItemId id;
  private final ItemProperties properties;

  protected DevItem(ItemId id, ItemProperties properties) {
    this.id = Objects.requireNonNull(id, "id");
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  public final ItemId id() {
    return this.id;
  }

  public final ItemProperties properties() {
    return this.properties;
  }

  public abstract boolean vanilla();

  public final DevItemStack createStack() {
    return this.createStack(1);
  }

  public final DevItemStack createStack(int amount) {
    if (amount < 1 || amount > this.properties.maximumStackSize()) {
      throw new IllegalArgumentException(
        "Invalid amount for " + String.valueOf(this.id) + ": " + amount
      );
    }
    ItemStack stack = this.createBaseStack(amount);
    if (stack == null || stack.getType().isAir()) {
      throw new IllegalStateException(
        "Item base stack must be non-air: " + String.valueOf(this.id)
      );
    }
    stack.setAmount(amount);
    DevItemStack result = new DevItemStack(this, stack);
    result.initialize();
    this.onCreated(result);
    return result;
  }

  protected void onCreated(DevItemStack stack) {}

  protected ItemStack createBaseStack(int amount) {
    return new ItemStack(this.properties.material(), amount);
  }

  protected void onLoaded(DevItemStack stack) {}

  /** Called only when ItemProperties.handTicking is enabled for this definition. */
  protected void onHandTick(DevItemStack stack, Player holder, EquipmentSlot hand) {}

  /** Called only when ItemProperties.inventoryTicking is enabled for this definition. */
  protected void onInventoryTick(DevItemStack stack, Player holder, int slot) {}

  /** Called once after Bukkit accepts consumption of this item. */
  protected void onConsumed(DevItemStack stack, Player consumer) {}

  /**
   * Called for an accepted direct melee hit when ItemProperties.attackTriggering is enabled.
   * The event is at MONITOR priority and must be treated as read-only.
   */
  protected void onAttack(
    DevItemStack stack,
    Player attacker,
    Entity target,
    EntityDamageByEntityEvent event
  ) {}

  protected List<String> renderLore(DevItemStack stack, Player viewer) {
    return List.of();
  }

  /** Renders status lines between the item header and its enchantments. */
  protected List<String> renderStatusLore(DevItemStack stack, Player viewer) {
    return List.of();
  }

  protected String renderName(DevItemStack stack, Player viewer) {
    return null;
  }

  /**
   * Renders the enchantment portion of this item's lore.
   *
   * <p>Vanilla overrides may replace this section without reimplementing the
   * common name, quality, category, or custom-lore pipeline.</p>
   */
  protected List<String> renderEnchantmentLore(DevItemStack stack, Player viewer) {
    return stack.renderEnchantmentLore(viewer);
  }

  final void load(DevItemStack stack) {
    this.onLoaded(stack);
  }

  final void consume(DevItemStack stack, Player consumer) {
    this.onConsumed(stack, consumer);
  }

  final void handTick(DevItemStack stack, Player holder, EquipmentSlot hand) {
    this.onHandTick(stack, holder, hand);
  }

  final void inventoryTick(DevItemStack stack, Player holder, int slot) {
    this.onInventoryTick(stack, holder, slot);
  }

  final void attack(
    DevItemStack stack,
    Player attacker,
    Entity target,
    EntityDamageByEntityEvent event
  ) {
    this.onAttack(stack, attacker, target, event);
  }

  final boolean behaviorsEnabled() {
    return !this.vanilla() || this instanceof OverrideVanillaItem;
  }

  final void render(DevItemStack stack, Player viewer) {
    String language = ItemTranslations.language(viewer);
    String name = stack
      .customName()
      .orElseGet(() -> {
        String rendered = this.renderName(stack, viewer);
        return rendered == null ? this.defaultName(language) : rendered;
      });
    // Build enchantment lore before invoking custom renderLore: subclasses may
    // inspect the final enchantment state as part of their own render process.
    List<String> enchantments = this.renderEnchantmentLore(stack, viewer);
    List<String> statusLore = this.renderStatusLore(stack, viewer);
    List<String> itemLore = this.renderLore(stack, viewer);
    List<String> abilityLore = this instanceof AbilityItem abilityItem
      ? AbilityDisplay.render(abilityItem.abilities(stack), viewer)
      : List.of();
    java.util.ArrayList<String> extraLore = new java.util.ArrayList<>(abilityLore);
    if (!abilityLore.isEmpty() && !itemLore.isEmpty()) extraLore.add("");
    extraLore.addAll(itemLore);
    stack.applyDisplay(this.properties.quality().colorCode() + name, statusLore, enchantments, extraLore, language);
  }

  public final Material material() {
    return this.properties.material();
  }

  private String nameKey() {
    return this.vanilla()
      ? "item.minecraft." + this.id.path()
      : "item." + String.valueOf(this.id) + ".name";
  }

  private String defaultName(String language) {
    return this.vanilla()
      ? ItemTranslations.minecraft(
        language,
        this.nameKey(),
        ItemTranslations.humanize(this.id.path()),
        new Object[0]
      )
      : ItemTranslations.translate(
        language,
        this.nameKey(),
        this.id.toString(),
        new Object[0]
      );
  }

  protected final String translate(
    Player viewer,
    String key,
    String fallback,
    Object... arguments
  ) {
    return ItemTranslations.translate(
      ItemTranslations.language(viewer),
      key,
      fallback,
      arguments
    );
  }

  protected final String minecraft(
    Player viewer,
    String key,
    String fallback,
    Object... arguments
  ) {
    return ItemTranslations.minecraft(
      ItemTranslations.language(viewer),
      key,
      fallback,
      arguments
    );
  }

  protected final String viewerLanguage(Player viewer) {
    return ItemTranslations.language(viewer);
  }
}
