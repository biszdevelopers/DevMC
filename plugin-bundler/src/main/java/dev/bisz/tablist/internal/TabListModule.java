/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 */
package dev.bisz.tablist.internal;

import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.tablist.TabListService;
import dev.bisz.tablist.internal.ProtocolLibTabListService;
import java.util.Set;
import org.bukkit.Bukkit;

public final class TabListModule implements BundlerModule {

  @Override
  public String id() {
    return "tablist";
  }

  @Override
  public Set<String> dependencies() {
    return Set.of("locales", "server-health");
  }

  @Override
  public void start(ModuleContext context) {
    if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
      throw new IllegalStateException("ProtocolLib is not installed");
    }
    context
      .services()
      .register(TabListService.class, new ProtocolLibTabListService());
  }

  @Override
  public void stop() {}
}
