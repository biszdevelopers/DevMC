/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.plugin.Plugin
 */
package dev.bisz.menus.internal;

import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.menus.MenuManager;
import java.util.Set;
import org.bukkit.plugin.Plugin;

public final class MenuModule implements BundlerModule {

  private MenuManager menus;

  @Override
  public String id() {
    return "menus";
  }

  @Override
  public Set<String> dependencies() {
    return Set.of();
  }

  @Override
  public void start(ModuleContext context) {
    this.menus = new MenuManager((Plugin) context.plugin());
    context.services().register(MenuManager.class, this.menus);
  }

  @Override
  public void stop() {
    if (this.menus != null) {
      this.menus.dispose();
      this.menus = null;
    }
  }
}
