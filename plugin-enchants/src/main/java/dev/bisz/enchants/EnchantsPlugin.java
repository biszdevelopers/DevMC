package dev.bisz.enchants;

import dev.bisz.bundler.JSON;
import dev.bisz.players.locales.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the custom enchanting-table plugin. */
public final class EnchantsPlugin extends JavaPlugin {
  private static EnchantsPlugin instance;
  private EnchantmentGenerator generator = new DefaultEnchantmentGenerator();
  private final List<CustomEnchantingModifier> modifiers = new ArrayList<>();
  private EnchantingMenuController menus;

  @Override public void onLoad() {
    instance = this;
    modifiers.add(new BookshelfEnchantmentModifier());
  }

  @Override public void onEnable() {
    installLocaleDefaults();
    menus = new EnchantingMenuController(this);
    getServer().getPluginManager().registerEvents(menus, this);
  }

  @Override public void onDisable() {
    if (menus != null) menus.dispose();
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
