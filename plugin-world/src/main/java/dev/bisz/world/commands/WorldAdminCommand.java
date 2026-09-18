package dev.bisz.world.commands;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.integration.SelectionRegion;
import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.Monument;
import dev.bisz.world.setup.SetupService;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Administrative settlement, region, and monument management. */
public final class WorldAdminCommand extends DevCommand {

  private static final int MAX_SELECTION_CHUNKS = 4096;

  private static final List<String> SUBCOMMANDS = List.of(
    "create",
    "delete",
    "addchunk",
    "removechunk",
    "setspawn",
    "setrent",
    "setperiod",
    "setplotsize",
    "monument",
    "setup",
    "pregen",
    "reload"
  );

  private final WorldPlugin plugin;

  public WorldAdminCommand(WorldPlugin plugin) {
    super("worldadmin", "world.admin");
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
        Locale.get(player, "settlement.command.usage", "/worldadmin <subcommand>")
      );
      return true;
    }
    switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
      case "create" -> create(player, arguments);
      case "delete" -> delete(player, arguments);
      case "addchunk" -> addChunk(player, arguments);
      case "removechunk" -> removeChunk(player, arguments);
      case "setspawn" -> setSpawn(player, arguments);
      case "setrent" -> setRent(player, arguments);
      case "setperiod" -> setPeriod(player, arguments);
      case "setplotsize" -> setPlotSize(player, arguments);
      case "monument" -> monument(player, arguments);
      case "setup" -> setup(player);
      case "pregen" -> pregen(player, arguments);
      case "reload" -> {
        plugin.reloadData();
        player.sendMessage(Locale.get(player, "settlement.reload"));
      }
      default -> player.sendMessage(
        Locale.get(player, "settlement.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void create(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/worldadmin create <name>")
      );
      return;
    }
    Settlement settlement = plugin
      .settlements()
      .create(arguments[1], player.getLocation());
    SelectionRegion selection = plugin.selection().selection(player);
    int added = 0;
    if (selection != null) {
      if (selection.chunkCount() > MAX_SELECTION_CHUNKS) {
        player.sendMessage(
          "§eSelection is large (" +
          selection.chunkCount() +
          " chunks); adding only the first " +
          MAX_SELECTION_CHUNKS +
          "."
        );
      }
      for (int cx = selection.minChunkX(); cx <= selection.maxChunkX(); cx++) {
        for (
          int cz = selection.minChunkZ();
          cz <= selection.maxChunkZ();
          cz++
        ) {
          if (added >= MAX_SELECTION_CHUNKS) break;
          if (
            plugin
              .settlements()
              .addChunk(
                settlement,
                new ChunkKey(selection.world(), cx, cz)
              )
          ) {
            added++;
          }
        }
        if (added >= MAX_SELECTION_CHUNKS) break;
      }
    }
    player.sendMessage(
      Locale.get(player, "settlement.settlement.created", settlement.name())
    );
    if (added > 0) {
      player.sendMessage(
        "§aAdded §f" +
        added +
        "§a chunk(s) from the WorldEdit selection."
      );
    } else if (selection == null) {
      player.sendMessage(
        "§7No WorldEdit selection; created a single-chunk settlement. " +
        "Make a selection and re-run create, or use /worldadmin addchunk."
      );
    }
  }

  private void delete(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/worldadmin delete <name>")
      );
      return;
    }
    Settlement settlement = plugin.settlements().byName(arguments[1]);
    if (settlement == null || !plugin.settlements().delete(settlement.id())) {
      player.sendMessage(Locale.get(player, "settlement.settlement.not_found"));
      return;
    }
    player.sendMessage(Locale.get(player, "settlement.settlement.deleted", settlement.name()));
  }

  private void addChunk(Player player, String[] arguments) {
    Settlement settlement = resolveSettlement(player, arguments);
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.not_found"));
      return;
    }
    if (plugin.settlements().addChunk(settlement, ChunkKey.of(player.getLocation()))) {
      player.sendMessage(Locale.get(player, "settlement.chunk.added", settlement.name()));
    } else {
      player.sendMessage(Locale.get(player, "settlement.chunk.already", settlement.name()));
    }
  }

  private void removeChunk(Player player, String[] arguments) {
    Settlement settlement = resolveSettlement(player, arguments);
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.not_found"));
      return;
    }
    if (plugin.settlements().removeChunk(settlement, ChunkKey.of(player.getLocation()))) {
      player.sendMessage(Locale.get(player, "settlement.chunk.removed", settlement.name()));
    } else {
      player.sendMessage(
        Locale.get(player, "settlement.chunk.not_present", settlement.name())
      );
    }
  }

  private void setSpawn(Player player, String[] arguments) {
    Settlement settlement = resolveSettlement(player, arguments);
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.not_found"));
      return;
    }
    settlement.spawn(player.getLocation());
    plugin.settlements().save();
    player.sendMessage(Locale.get(player, "settlement.settlement.spawn.set", settlement.name()));
  }

  private void setRent(Player player, String[] arguments) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null || arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/worldadmin setrent <nits>")
      );
      return;
    }
    settlement.rentPrice(parseLong(arguments[1], settlement.rentPrice()));
    plugin.settlements().save();
    player.sendMessage(
      Locale.get(player, "settlement.settlement.rent.set", settlement.rentPrice())
    );
  }

  private void setPeriod(Player player, String[] arguments) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null || arguments.length < 2) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin setperiod <seconds>"
        )
      );
      return;
    }
    long seconds = parseLong(arguments[1], settlement.rentPeriodMillis() / 1000L);
    settlement.rentPeriodMillis(seconds * 1000L);
    plugin.settlements().save();
    player.sendMessage(
      Locale.get(player, "settlement.settlement.period.set", seconds)
    );
  }

  private void setPlotSize(Player player, String[] arguments) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null || arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/worldadmin setplotsize <size>")
      );
      return;
    }
    int size = (int) parseLong(arguments[1], settlement.plotSize());
    settlement.plotSize(size);
    plugin.settlements().save();
    player.sendMessage(Locale.get(player, "settlement.settlement.plotsize.set", settlement.plotSize()));
  }

  private void monument(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin monument <add|remove|list>"
        )
      );
      return;
    }
    switch (arguments[1].toLowerCase(java.util.Locale.ROOT)) {
      case "add" -> monumentAdd(player, arguments);
      case "remove" -> monumentRemove(player, arguments);
      case "list" -> monumentList(player);
      default -> player.sendMessage(
        Locale.get(player, "settlement.command.unknown", arguments[1])
      );
    }
  }

  private void monumentAdd(Player player, String[] arguments) {
    if (arguments.length < 3) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin monument add <id> [tier] [table]"
        )
      );
      return;
    }
    String id = arguments[2];
    int tier = arguments.length > 3
      ? (int) parseLong(arguments[3], 1L)
      : 1;
    String table = arguments.length > 4 ? arguments[4] : "tier" + tier;
    var location = player.getLocation();
    Monument monument = new Monument(
      id,
      location.getWorld().getName(),
      location.getBlockX() - 16,
      location.getBlockY() - 16,
      location.getBlockZ() - 16,
      location.getBlockX() + 16,
      location.getBlockY() + 16,
      location.getBlockZ() + 16,
      tier,
      table,
      Math.max(1L, tier) * 36_000L,
      tier * 0.5,
      null
    );
    plugin.monuments().add(monument);
    player.sendMessage(Locale.get(player, "settlement.monument.added", id));
  }

  private void monumentRemove(Player player, String[] arguments) {
    if (arguments.length < 3) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin monument remove <id>"
        )
      );
      return;
    }
    if (plugin.monuments().remove(arguments[2])) {
      player.sendMessage(
        Locale.get(player, "settlement.monument.removed", arguments[2])
      );
    } else {
      player.sendMessage(Locale.get(player, "settlement.monument.none"));
    }
  }

  private void monumentList(Player player) {
    for (Monument monument : plugin.monuments().all()) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.monument.entry",
          monument.id(),
          monument.tier(),
          monument.lootTableId()
        )
      );
    }
  }

  private void setup(Player player) {
    SetupService.Report report = plugin.setup().run(player.getLocation());
    for (SetupService.Step step : report.steps()) {
      player.sendMessage(
        (step.ok() ? "§a" : "§c") + step.name() + "§7: " + step.detail()
      );
    }
    player.sendMessage(
      Locale.get(
        player,
        report.success() ? "settlement.setup.success" : "settlement.setup.failed"
      )
    );
  }

  private void pregen(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin pregen <radius|stop>"
        )
      );
      return;
    }
    if (arguments[1].equalsIgnoreCase("stop")) {
      plugin.pregenerator().stop();
      player.sendMessage(Locale.get(player, "settlement.pregen.stopped"));
      return;
    }
    long radius = parseLong(arguments[1], -1L);
    if (radius <= 0L) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin pregen <radius|stop>"
        )
      );
      return;
    }
    plugin
      .pregenerator()
      .start(player.getWorld(), (int) radius, player);
    player.sendMessage(
      Locale.get(player, "settlement.pregen.started", radius)
    );
  }

  private Settlement resolveSettlement(Player player, String[] arguments) {
    if (arguments.length > 1) {
      Settlement named = plugin.settlements().byName(arguments[1]);
      if (named != null) return named;
    }
    return plugin.settlements().settlementAt(player.getLocation());
  }

  private static long parseLong(String value, long fallback) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      return fallback;
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
      if (arguments[0].equalsIgnoreCase("monument")) {
        return List.of("add", "remove", "list");
      }
      if (arguments[0].equalsIgnoreCase("pregen")) {
        return List.of("stop");
      }
      return plugin.settlements().all().stream().map(Settlement::name).toList();
    }
    return List.of();
  }
}
