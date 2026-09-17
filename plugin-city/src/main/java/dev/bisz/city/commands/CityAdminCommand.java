package dev.bisz.city.commands;

import dev.bisz.city.CityPlugin;
import dev.bisz.city.model.ChunkKey;
import dev.bisz.city.model.City;
import dev.bisz.city.model.Monument;
import dev.bisz.city.setup.SetupService;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Administrative city, region, and monument management. */
public final class CityAdminCommand extends DevCommand {

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

  private final CityPlugin plugin;

  public CityAdminCommand(CityPlugin plugin) {
    super("cityadmin", "city.admin");
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
        Locale.get(player, "city.command.usage", "/cityadmin <subcommand>")
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
        player.sendMessage(Locale.get(player, "city.reload"));
      }
      default -> player.sendMessage(
        Locale.get(player, "city.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void create(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "city.command.usage", "/cityadmin create <name>")
      );
      return;
    }
    City city = plugin.cities().create(arguments[1], player.getLocation());
    player.sendMessage(Locale.get(player, "city.city.created", city.name()));
  }

  private void delete(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "city.command.usage", "/cityadmin delete <name>")
      );
      return;
    }
    City city = plugin.cities().byName(arguments[1]);
    if (city == null || !plugin.cities().delete(city.id())) {
      player.sendMessage(Locale.get(player, "city.city.not_found"));
      return;
    }
    player.sendMessage(Locale.get(player, "city.city.deleted", city.name()));
  }

  private void addChunk(Player player, String[] arguments) {
    City city = resolveCity(player, arguments);
    if (city == null) {
      player.sendMessage(Locale.get(player, "city.city.not_found"));
      return;
    }
    if (plugin.cities().addChunk(city, ChunkKey.of(player.getLocation()))) {
      player.sendMessage(Locale.get(player, "city.chunk.added", city.name()));
    } else {
      player.sendMessage(Locale.get(player, "city.chunk.already", city.name()));
    }
  }

  private void removeChunk(Player player, String[] arguments) {
    City city = resolveCity(player, arguments);
    if (city == null) {
      player.sendMessage(Locale.get(player, "city.city.not_found"));
      return;
    }
    if (plugin.cities().removeChunk(city, ChunkKey.of(player.getLocation()))) {
      player.sendMessage(Locale.get(player, "city.chunk.removed", city.name()));
    } else {
      player.sendMessage(
        Locale.get(player, "city.chunk.not_present", city.name())
      );
    }
  }

  private void setSpawn(Player player, String[] arguments) {
    City city = resolveCity(player, arguments);
    if (city == null) {
      player.sendMessage(Locale.get(player, "city.city.not_found"));
      return;
    }
    city.spawn(player.getLocation());
    plugin.cities().save();
    player.sendMessage(Locale.get(player, "city.city.spawn.set", city.name()));
  }

  private void setRent(Player player, String[] arguments) {
    City city = plugin.cities().cityAt(player.getLocation());
    if (city == null || arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "city.command.usage", "/cityadmin setrent <nits>")
      );
      return;
    }
    city.rentPrice(parseLong(arguments[1], city.rentPrice()));
    plugin.cities().save();
    player.sendMessage(
      Locale.get(player, "city.city.rent.set", city.rentPrice())
    );
  }

  private void setPeriod(Player player, String[] arguments) {
    City city = plugin.cities().cityAt(player.getLocation());
    if (city == null || arguments.length < 2) {
      player.sendMessage(
        Locale.get(
          player,
          "city.command.usage",
          "/cityadmin setperiod <seconds>"
        )
      );
      return;
    }
    long seconds = parseLong(arguments[1], city.rentPeriodMillis() / 1000L);
    city.rentPeriodMillis(seconds * 1000L);
    plugin.cities().save();
    player.sendMessage(
      Locale.get(player, "city.city.period.set", seconds)
    );
  }

  private void setPlotSize(Player player, String[] arguments) {
    City city = plugin.cities().cityAt(player.getLocation());
    if (city == null || arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "city.command.usage", "/cityadmin setplotsize <size>")
      );
      return;
    }
    int size = (int) parseLong(arguments[1], city.plotSize());
    city.plotSize(size);
    plugin.cities().save();
    player.sendMessage(Locale.get(player, "city.city.plotsize.set", city.plotSize()));
  }

  private void monument(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(
          player,
          "city.command.usage",
          "/cityadmin monument <add|remove|list>"
        )
      );
      return;
    }
    switch (arguments[1].toLowerCase(java.util.Locale.ROOT)) {
      case "add" -> monumentAdd(player, arguments);
      case "remove" -> monumentRemove(player, arguments);
      case "list" -> monumentList(player);
      default -> player.sendMessage(
        Locale.get(player, "city.command.unknown", arguments[1])
      );
    }
  }

  private void monumentAdd(Player player, String[] arguments) {
    if (arguments.length < 3) {
      player.sendMessage(
        Locale.get(
          player,
          "city.command.usage",
          "/cityadmin monument add <id> [tier] [table]"
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
    player.sendMessage(Locale.get(player, "city.monument.added", id));
  }

  private void monumentRemove(Player player, String[] arguments) {
    if (arguments.length < 3) {
      player.sendMessage(
        Locale.get(
          player,
          "city.command.usage",
          "/cityadmin monument remove <id>"
        )
      );
      return;
    }
    if (plugin.monuments().remove(arguments[2])) {
      player.sendMessage(
        Locale.get(player, "city.monument.removed", arguments[2])
      );
    } else {
      player.sendMessage(Locale.get(player, "city.monument.none"));
    }
  }

  private void monumentList(Player player) {
    for (Monument monument : plugin.monuments().all()) {
      player.sendMessage(
        Locale.get(
          player,
          "city.monument.entry",
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
        report.success() ? "city.setup.success" : "city.setup.failed"
      )
    );
  }

  private void pregen(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(
          player,
          "city.command.usage",
          "/cityadmin pregen <radius|stop>"
        )
      );
      return;
    }
    if (arguments[1].equalsIgnoreCase("stop")) {
      plugin.pregenerator().stop();
      player.sendMessage(Locale.get(player, "city.pregen.stopped"));
      return;
    }
    long radius = parseLong(arguments[1], -1L);
    if (radius <= 0L) {
      player.sendMessage(
        Locale.get(
          player,
          "city.command.usage",
          "/cityadmin pregen <radius|stop>"
        )
      );
      return;
    }
    plugin
      .pregenerator()
      .start(player.getWorld(), (int) radius, player);
    player.sendMessage(
      Locale.get(player, "city.pregen.started", radius)
    );
  }

  private City resolveCity(Player player, String[] arguments) {
    if (arguments.length > 1) {
      City named = plugin.cities().byName(arguments[1]);
      if (named != null) return named;
    }
    return plugin.cities().cityAt(player.getLocation());
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
      return plugin.cities().all().stream().map(City::name).toList();
    }
    return List.of();
  }
}
