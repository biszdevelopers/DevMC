/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Server
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandMap
 *  org.bukkit.plugin.Plugin
 */
package dev.bisz.commands;

import dev.bisz.commands.DevCommand;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;

public final class DevCommandRegistry {

  public static final String FALLBACK_PREFIX = "devcommand";
  private final CommandMap commandMap;
  private final Map<Plugin, List<DevCommand>> commands = new IdentityHashMap<
    Plugin,
    List<DevCommand>
  >();

  public DevCommandRegistry(Server server) {
    this.commandMap = DevCommandRegistry.commandMap(
      Objects.requireNonNull(server, "server")
    );
  }

  public synchronized void register(Plugin owner, DevCommand command) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(command, "command");
    if (!this.commandMap.register(FALLBACK_PREFIX, (Command) command)) {
      throw new IllegalArgumentException(
        "Command label is already registered: " + command.getName()
      );
    }
    this.commands.computeIfAbsent(owner, ignored -> new ArrayList()).add(
      command
    );
  }

  public synchronized void unregisterAll(Plugin owner) {
    List<DevCommand> owned = this.commands.remove(owner);
    if (owned != null) {
      owned.forEach(command -> command.unregister(this.commandMap));
    }
  }

  private static CommandMap commandMap(Server server) {
    try {
      Method method = server
        .getClass()
        .getMethod("getCommandMap", new Class[0]);
      return (CommandMap) method.invoke(server, new Object[0]);
    } catch (ReflectiveOperationException ignored) {
      try {
        Field field = server.getClass().getDeclaredField("commandMap");
        field.setAccessible(true);
        return (CommandMap) field.get(server);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException(
          "This server does not expose a Bukkit CommandMap",
          exception
        );
      }
    }
  }
}
