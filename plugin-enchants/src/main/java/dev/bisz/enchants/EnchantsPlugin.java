package dev.bisz.enchants;

import dev.bisz.bundler.JSON;
import dev.bisz.commands.CommandRegistery;
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
  private dev.bisz.enchants.fishing.FishingManager fishing;
  private ResourcePackHost resourcePack;
  private EnchantsConfig config;

  @Override public void onLoad() {
    instance = this;
    config = new EnchantsConfig(this);
    config.load();
    SocketLayouts.load(config.sockets(), config.categories());
    modifiers.add(new BookshelfEnchantmentModifier());
    ItemsPlugin.instance().enchantments().registerAll(this, EnchantmentCatalog.customDefinitions());
    EnchantmentCatalog.load(config.enchantments());
    for (Material material : Material.values()) {
      if (SocketedVanillaItem.supports(material)) {
        ItemsPlugin.instance().registry().registerVanillaOverride(this, SocketedItemOverrides.create(material));
      }
    }
  }

  @Override public void onEnable() {
    installLocaleDefaults();
    LinearExperience.setPointsPerLevel(config.config().getInt("xp.points-per-level", LinearExperience.DEFAULT_POINTS_PER_LEVEL));
    DamageIndicator.setEnabled(config.config().getBoolean("display.damage-indicators", true));
    costs = EnchantingCosts.load(config);
    menus = new EnchantingMenuController(this);
    getServer().getPluginManager().registerEvents(menus, this);
    effects = new EnchantmentEffectsListener(this);
    getServer().getPluginManager().registerEvents(effects, this);
    fishing = new dev.bisz.enchants.fishing.FishingManager(this, config.fishing());
    getServer().getPluginManager().registerEvents(fishing, this);
    getServer().getPluginManager().registerEvents(new GrindstoneListener(this), this);
    experience = new LinearExperienceListener(this);
    getServer().getPluginManager().registerEvents(experience, this);
    resourcePack = new ResourcePackHost(this);
    resourcePack.start();
    getServer().getPluginManager().registerEvents(resourcePack, this);
    CommandRegistery.register(this, new EnchantsCommand(this));
  }

  @Override public void onDisable() {
    if (menus != null) menus.dispose();
    if (effects != null) effects.dispose();
    if (resourcePack != null) resourcePack.stop();
    CommandRegistery.unregisterAll(this);
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

  EnchantsConfig config() {
    return Objects.requireNonNull(config, "Enchants config is not loaded");
  }

  /** Reloads the YAML configuration and rebuilds the derived catalog, layouts, and costs. */
  public void reload() {
    config.load();
    SocketLayouts.load(config.sockets(), config.categories());
    EnchantmentCatalog.load(config.enchantments());
    costs = EnchantingCosts.load(config);
    LinearExperience.setPointsPerLevel(
      config.config().getInt("xp.points-per-level", LinearExperience.DEFAULT_POINTS_PER_LEVEL));
    DamageIndicator.setEnabled(config.config().getBoolean("display.damage-indicators", true));
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
