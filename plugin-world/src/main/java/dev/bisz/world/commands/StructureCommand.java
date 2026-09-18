package dev.bisz.world.commands;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.menus.StructureBookMenu;
import dev.bisz.world.structure.StructureDefinition;
import dev.bisz.world.structure.StructureInstance;
import dev.bisz.world.structure.VanillaStructure;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.ArrayList;
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
    "book",
    "preview",
    "stage",
    "save",
    "cancel",
    "reload"
  );

  private final WorldPlugin plugin;

  public StructureCommand(WorldPlugin plugin) {
    super("structure", "world.admin");
    this.plugin = plugin;
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] arguments
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Locale.get("en_us", "settlement.command.player_only"));
      return true;
    }
    if (arguments.length == 0) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/structure <subcommand>")
      );
      return true;
    }
    switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
      case "list" -> list(player);
      case "paste" -> paste(player, arguments, null);
      case "event" -> paste(player, arguments, arguments.length > 1 ? arguments[1] : null);
      case "remove" -> remove(player, arguments);
      case "instances" -> instances(player);
      case "book" -> StructureBookMenu.open(plugin, player);
      case "preview", "stage" -> preview(player, arguments);
      case "save" -> plugin.previews().save(player);
      case "cancel" -> cancel(player);
      case "reload" -> {
        plugin.structures().reload();
        player.sendMessage(Locale.get(player, "settlement.structure.reload"));
      }
      default -> player.sendMessage(
        Locale.get(player, "settlement.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void list(Player player) {
    player.sendMessage(
      Locale.get(
        player,
        "settlement.structure.list.header",
        plugin.structures().registry().all().size()
      )
    );
    for (StructureDefinition definition : plugin.structures().registry().all()) {
      player.sendMessage(
        "§7- §f" +
        definition.id() +
        " §8[" +
        definition.type() +
        "] §7" +
        definition.file()
      );
    }
  }

  /** Opens a file-backed preview of a structure for editing. */
  private void preview(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/structure preview <id>")
      );
      return;
    }
    plugin.previews().open(player, arguments[1]);
  }

  private void cancel(Player player) {
    if (!plugin.previews().cancel(player)) {
      player.sendMessage("§cNo structure preview is open.");
    }
  }

  private void paste(Player player, String[] arguments, String eventId) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/structure paste <id>")
      );
      return;
    }
    StructureInstance instance = plugin
      .structures()
      .spawn(arguments[1], player.getLocation(), eventId);
    if (instance == null) {
      player.sendMessage(Locale.get(player, "settlement.structure.paste_failed"));
      return;
    }
    player.sendMessage(
      Locale.get(player, "settlement.structure.pasted", instance.instanceId())
    );
  }

  private void remove(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/structure remove <instance>")
      );
      return;
    }
    if (plugin.structures().despawn(arguments[1], true)) {
      player.sendMessage(
        Locale.get(player, "settlement.structure.removed", arguments[1])
      );
    } else {
      player.sendMessage(Locale.get(player, "settlement.structure.remove_failed"));
    }
  }

  private void instances(Player player) {
    player.sendMessage(
      Locale.get(
        player,
        "settlement.structure.instances.header",
        plugin.structures().instances().size()
      )
    );
    for (StructureInstance instance : plugin.structures().instances()) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.structure.instances.entry",
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
      List<String> ids = new ArrayList<>();
      for (StructureDefinition definition : plugin.structures().registry().all()) {
        ids.add(definition.id());
      }
      for (VanillaStructure entry : plugin.vanillaStructures().all()) {
        ids.add(entry.id());
      }
      return ids;
    }
    return List.of();
  }
}
