/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.bundler.internal;

import dev.bisz.bundler.internal.ServiceRegistry;
import dev.bisz.catalogs.StaticCatalogService;
import dev.bisz.storage.JsonDatabase;
import org.bukkit.plugin.java.JavaPlugin;

public record ModuleContext(
  JavaPlugin plugin,
  ServiceRegistry services,
  JsonDatabase database,
  StaticCatalogService catalogs
) {}
