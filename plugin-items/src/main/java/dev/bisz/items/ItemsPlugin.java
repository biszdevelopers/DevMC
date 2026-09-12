/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  dev.bisz.bundler.BundlerPlugin
 *  dev.bisz.commands.DevCommand
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.items;

import dev.bisz.bundler.JSON;
import dev.bisz.commands.CommandRegistery;
import dev.bisz.commands.DevCommand;
import dev.bisz.items.GiveItemCommand;
import dev.bisz.items.ItemFactory;
import dev.bisz.items.ItemRegistry;
import dev.bisz.items.ItemRuntimeListener;
import dev.bisz.players.locales.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemsPlugin extends JavaPlugin {

  private static ItemsPlugin instance;
  private final ItemRegistry registry = new ItemRegistry();
  private final EnchantmentRegistry enchantments = new EnchantmentRegistry();
  private ItemFactory factory;

  public static ItemsPlugin instance() {
    return Objects.requireNonNull(instance, "Items plugin is not loaded");
  }

  public void onLoad() {
    instance = this;
    this.registry.registerVanillaItems();
    this.enchantments.registerVanillaEnchantments();
    this.enchantments.register(this, new RainbowEnchantment());
  }

  public void onEnable() {
    this.installLocaleDefaults();
    this.factory = new ItemFactory(this.registry);
    this.getServer()
      .getPluginManager()
      .registerEvents(
        (Listener) new ItemRuntimeListener(this, this.factory),
        (Plugin) this
      );
    this.getServer()
      .getPluginManager()
      .registerEvents(
        (Listener) new EnchantmentRuntimeListener(this, this.factory, this.registry, this.enchantments),
        (Plugin) this
      );
    CommandRegistery.register(
      (Plugin) this,
      (DevCommand) new GiveItemCommand(this.factory, this.registry)
    );
    CommandRegistery.register(
      (Plugin) this,
      (DevCommand) new GiveRandomThingsCommand(this.factory, this.registry)
    );
    CommandRegistery.register(
      (Plugin) this,
      (DevCommand) new ForceRenderCommand(this.factory)
    );
    CommandRegistery.register(
      (Plugin) this,
      (DevCommand) new DevEnchantCommand(this.factory, this.enchantments)
    );
  }

  public void onDisable() {
    CommandRegistery.unregisterAll((Plugin) this);
    this.registry.unregisterAll((Plugin) this);
    this.enchantments.unregisterAll((Plugin) this);
    if (instance == this) {
      instance = null;
    }
  }

  public ItemRegistry registry() {
    return this.registry;
  }

  public ItemFactory factory() {
    return Objects.requireNonNull(this.factory, "Items plugin is not enabled");
  }

  public EnchantmentRegistry enchantments() {
    return this.enchantments;
  }

  private void installLocaleDefaults() {
    for (String language : Locale.availableLanguages()) {
      String resource = "lang/" + language + ".json";
      try {
        InputStream input = this.getResource(resource);
        try {
          if (input == null) {
            throw new IllegalStateException(
              "Missing bundled Items locale file " + resource
            );
          }
          JSON.mergeMissingDefaults("lang/" + language + ".json", input);
        } finally {
          if (input == null) continue;
          input.close();
        }
      } catch (IOException exception) {
        throw new IllegalStateException(
          "Cannot close bundled Items locale file " + resource,
          exception
        );
      }
    }
    Locale.reload();
  }
}
