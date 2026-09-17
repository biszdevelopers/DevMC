package dev.bisz.city.commands;

import dev.bisz.city.CityPlugin;
import dev.bisz.city.structure.StructureDefinition;
import dev.bisz.city.structure.StructureInstance;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Administrative control of the custom structure system. */
public final class StructureCommand extends DevCommand {

  private static final List<String> SUBCOMMANDS = List.of(
    "list",
    "paste",
    "event",
    "remove",
    "instances",
    "reload"
  );

  private final CityPlugin plugin;

  public StructureCommand(CityPlugin plugin) {
    super("structure", "city.admin");
    this.plugin = plugin;
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] arguments
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Locale.get("en_us", "city.command.player_only"));
      return true;
    }
    if (arguments.length == 0) {
      player.sendMessage(
        Locale.get(player, "city.command.usage", "/structure <subcommand>")
      );
      return true;
    }
    switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
      case "list" -> list(player);
      case "paste" -> paste(player, arguments, null);
      case "event" -> paste(player, arguments, arguments.length > 1 ? arguments[1] : null);
      case "remove" -> remove(player, arguments);
      case "instances" -> instances(player);
      case "reload" -> {
        plugin.structures().reload();
        player.sendMessage(Locale.get(player, "city.structure.reload"));
      }
      default -> player.sendMessage(
        Locale.get(player, "city.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void list(Player player) {
    player.sendMessage(
      Locale.get(
        player,
        "city.structure.list.header",
        plugin.structures().registry().all().size()
      )
    );
    for (StructureDefinition definition : plugin.structures().registry().all()) {
      player.sendMessage(
        Locale.get(
          player,
          "city.structure.list.entry",
          definition.id(),
          definition.category(),
          definition.file()
        )
      );
    }
  }

  private void paste(Player player, String[] arguments, String eventId) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "city.command.usage", "/structure paste <id>")
      );
      return;
    }
    StructureInstance instance = plugin
      .structures()
      .spawn(arguments[1], player.getLocation(), eventId);
    if (instance == null) {
      player.sendMessage(Locale.get(player, "city.structure.paste_failed"));
      return;
    }
    player.sendMessage(
      Locale.get(player, "city.structure.pasted", instance.instanceId())
    );
  }

  private void remove(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "city.command.usage", "/structure remove <instance>")
      );
      return;
    }
    if (plugin.structures().despawn(arguments[1], true)) {
      player.sendMessage(
        Locale.get(player, "city.structure.removed", arguments[1])
      );
    } else {
      player.sendMessage(Locale.get(player, "city.structure.remove_failed"));
    }
  }

  private void instances(Player player) {
    player.sendMessage(
      Locale.get(
        player,
        "city.structure.instances.header",
        plugin.structures().instances().size()
      )
    );
    for (StructureInstance instance : plugin.structures().instances()) {
      player.sendMessage(
        Locale.get(
          player,
          "city.structure.instances.entry",
          instance.instanceId(),
          instance.definitionId(),
          instance.bounds().world()
        )
      );
    }
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    if (arguments.length <= 1) return SUBCOMMANDS;
    if (arguments.length == 2) {
      if (arguments[0].equalsIgnoreCase("remove")) {
        return plugin
          .structures()
          .instances()
          .stream()
          .map(StructureInstance::instanceId)
          .toList();
      }
      return plugin
        .structures()
        .registry()
        .all()
        .stream()
        .map(StructureDefinition::id)
        .toList();
    }
    return List.of();
  }
}
