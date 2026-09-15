package dev.bisz.enchants;

import dev.bisz.bundler.JSON;
import dev.bisz.players.locales.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.enchants.items.SocketedItemOverrides;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the custom enchanting-table plugin. */
public final class EnchantsPlugin extends JavaPlugin {
  private static EnchantsPlugin instance;
  private EnchantmentGenerator generator = new DefaultEnchantmentGenerator();
  private final List<CustomEnchantingModifier> modifiers = new ArrayList<>();
  private EnchantingMenuController menus;
  private EnchantingCosts costs;
  private EnchantmentEffectsListener effects;
  private LinearExperienceListener experience;

  @Override public void onLoad() {
    instance = this;
    modifiers.add(new BookshelfEnchantmentModifier());
    ItemsPlugin.instance().enchantments().registerAll(this, EnchantmentCatalog.customDefinitions());
    for (Material material : Material.values()) {
      if (SocketedVanillaItem.supports(material)) {
        ItemsPlugin.instance().registry().registerVanillaOverride(this, SocketedItemOverrides.create(material));
      }
    }
  }

  @Override public void onEnable() {
    installLocaleDefaults();
    costs = EnchantingCosts.load(this);
    menus = new EnchantingMenuController(this);
    getServer().getPluginManager().registerEvents(menus, this);
    effects = new EnchantmentEffectsListener(this);
    getServer().getPluginManager().registerEvents(effects, this);
    experience = new LinearExperienceListener(this);
    getServer().getPluginManager().registerEvents(experience, this);
  }

  @Override public void onDisable() {
    if (menus != null) menus.dispose();
    if (effects != null) effects.dispose();
    ItemsPlugin.instance().registry().unregisterAll(this);
    ItemsPlugin.instance().enchantments().unregisterAll(this);
    if (instance == this) instance = null;
  }

  public static EnchantsPlugin instance() {
    return Objects.requireNonNull(instance, "Enchants plugin is not loaded");
  }

  /** Returns the currently installed adjustable offer generator. */
  public EnchantmentGenerator enchantmentGenerator() { return generator; }

  /** Replaces the offer generator used for subsequent menu regenerations. */
  public void enchantmentGenerator(EnchantmentGenerator generator) {
    this.generator = Objects.requireNonNull(generator, "generator");
  }

  /** Conventional setter alias for integrations. */
  public void setEnchantmentGenerator(EnchantmentGenerator generator) {
    enchantmentGenerator(generator);
  }

  /** Registers a block modifier by its unique namespaced id. */
  public void registerModifier(CustomEnchantingModifier modifier) {
    Objects.requireNonNull(modifier, "modifier");
    if (modifiers.stream().anyMatch(existing -> existing.id().equals(modifier.id()))) {
      throw new IllegalArgumentException("Duplicate enchanting modifier id: " + modifier.id());
    }
    modifiers.add(modifier);
  }

  /** Returns modifiers in their menu display and scan order. */
  public List<CustomEnchantingModifier> enchantingModifiers() {
    return List.copyOf(modifiers);
  }

  EnchantingCosts enchantingCosts() {
    return Objects.requireNonNull(costs, "Enchantment costs are not loaded");
  }

  private void installLocaleDefaults() {
    for (String language : Locale.availableLanguages()) {
      String resource = "lang/" + language + ".json";
      try (InputStream input = getResource(resource)) {
        if (input == null) throw new IllegalStateException("Missing bundled Enchants locale file " + resource);
        JSON.mergeMissingDefaults(resource, input);
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot close bundled Enchants locale file " + resource, exception);
      }
    }
    Locale.reload();
  }
}
