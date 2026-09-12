/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.players.locales.internal;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.menus.MenuManager;
import dev.bisz.players.ProfileService;
import dev.bisz.players.locales.LocaleCommand;
import dev.bisz.players.locales.LocaleService;
import dev.bisz.players.locales.internal.DefaultLocaleService;
import java.util.Set;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class LocaleModule implements BundlerModule {

  @Override
  public String id() {
    return "locales";
  }

  @Override
  public Set<String> dependencies() {
    return Set.of("profiles", "menus");
  }

  @Override
  public void start(ModuleContext context) {
    DefaultLocaleService locales = new DefaultLocaleService(
      context.services().require(ProfileService.class),
      context.database(),
      context.plugin()
    );
    context.services().register(LocaleService.class, locales);
    JavaPlugin javaPlugin = context.plugin();
    if (!(javaPlugin instanceof BundlerPlugin)) {
      throw new IllegalStateException("Locale module requires BundlerPlugin");
    }
    BundlerPlugin plugin = (BundlerPlugin) javaPlugin;
    plugin
      .commandRegistry()
      .register(
        (Plugin) plugin,
        new LocaleCommand(
          plugin,
          locales,
          context.services().require(MenuManager.class)
        )
      );
  }

  @Override
  public void stop() {}
}
