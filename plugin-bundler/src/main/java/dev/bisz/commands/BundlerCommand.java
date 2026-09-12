/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 */
package dev.bisz.commands;

import dev.bisz.bundler.internal.ModuleDescriptor;
import dev.bisz.bundler.internal.ModuleManager;
import dev.bisz.catalogs.StaticCatalogService;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class BundlerCommand implements CommandExecutor {

  private final ModuleManager modules;
  private final StaticCatalogService catalogs;

  public BundlerCommand(ModuleManager modules, StaticCatalogService catalogs) {
    this.modules = modules;
    this.catalogs = catalogs;
  }

  public boolean onCommand(
    CommandSender sender,
    Command command,
    String label,
    String[] arguments
  ) {
    if (arguments.length == 0 || arguments[0].equalsIgnoreCase("status")) {
      String states = this.modules.descriptors()
        .stream()
        .map(ModuleDescriptor::id)
        .collect(Collectors.joining(", "));
      sender.sendMessage("Bundler modules: " + states);
      return true;
    }
    if (arguments[0].equalsIgnoreCase("reload")) {
      try {
        this.catalogs.reload();
        sender.sendMessage("Bundler static catalogs reloaded.");
      } catch (RuntimeException exception) {
        sender.sendMessage("Bundler reload failed: " + exception.getMessage());
      }
      return true;
    }
    return false;
  }
}
