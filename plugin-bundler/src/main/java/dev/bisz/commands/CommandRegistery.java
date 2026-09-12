package dev.bisz.commands;

import dev.bisz.bundler.BundlerPlugin;
import org.bukkit.plugin.Plugin;

/** Legacy-spelled static command registration helper. */
public final class CommandRegistery {

  private CommandRegistery() {}

  public static void register(DevCommand command) {
    register(BundlerPlugin.instance(), command);
  }

  public static void register(Plugin owner, DevCommand command) {
    BundlerPlugin.instance().commandRegistry().register(owner, command);
  }

  public static void unregisterAll(Plugin owner) {
    BundlerPlugin.instance().commandRegistry().unregisterAll(owner);
  }
}
