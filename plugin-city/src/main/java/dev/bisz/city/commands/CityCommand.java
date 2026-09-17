package dev.bisz.city.commands;

import dev.bisz.city.CityPlugin;
import dev.bisz.city.model.City;
import dev.bisz.city.model.Plot;
import dev.bisz.city.model.PlotId;
import dev.bisz.city.plot.PlotGrid;
import dev.bisz.city.menus.CityMenu;
import dev.bisz.city.menus.PlotMenu;
import dev.bisz.city.menus.VendorMenu;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Player-facing city and plot command. */
public final class CityCommand extends DevCommand {

  private static final List<String> SUBCOMMANDS = List.of(
    "list",
    "info",
    "spawn",
    "claim",
    "rent",
    "release",
    "plots",
    "vendor"
  );

  private final CityPlugin plugin;

  public CityCommand(CityPlugin plugin) {
    super("city", "city.use");
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
      CityMenu.open(plugin, player);
      return true;
    }
    switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
      case "list" -> list(player);
      case "info" -> info(player);
      case "spawn" -> spawn(player, arguments);
      case "claim" -> claim(player);
      case "rent" -> renew(player);
      case "release" -> release(player);
      case "plots" -> PlotMenu.open(plugin, player);
      case "vendor" -> VendorMenu.open(plugin, player);
      default -> player.sendMessage(
        Locale.get(player, "city.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void list(Player player) {
    player.sendMessage(
      Locale.get(player, "city.command.list.header", plugin.cities().all().size())
    );
    for (City city : plugin.cities().all()) {
      player.sendMessage(
        Locale.get(
          player,
          "city.command.list.entry",
          city.name(),
          city.chunks().size(),
          city.rentPrice()
        )
      );
    }
  }

  private void info(Player player) {
    City city = plugin.cities().cityAt(player.getLocation());
    if (city == null) {
      player.sendMessage(Locale.get(player, "city.city.none"));
      return;
    }
    player.sendMessage(
      Locale.get(
        player,
        "city.city.info",
        city.name(),
        city.chunks().size(),
        city.rentPrice()
      )
    );
  }

  private void spawn(Player player, String[] arguments) {
    City city = arguments.length > 1
      ? plugin.cities().byName(arguments[1])
      : plugin.cities().cityAt(player.getLocation());
    if (city == null) {
      player.sendMessage(Locale.get(player, "city.city.not_found"));
      return;
    }
    Location spawn = city.spawn();
    if (spawn == null) {
      player.sendMessage(Locale.get(player, "city.city.spawn.unset"));
      return;
    }
    player.teleport(spawn);
    player.sendMessage(Locale.get(player, "city.city.spawn.teleport", city.name()));
  }

  private void claim(Player player) {
    City city = plugin.cities().cityAt(player.getLocation());
    if (city == null) {
      player.sendMessage(Locale.get(player, "city.city.none"));
      return;
    }
    PlotId id = PlotGrid.plotAt(
      city,
      player.getLocation().getBlockX(),
      player.getLocation().getBlockZ(),
      0
    );
    Plot plot = plugin.plots().claim(city, id, player.getUniqueId());
    if (plot == null) {
      player.sendMessage(Locale.get(player, "city.plot.unavailable"));
      return;
    }
    player.sendMessage(Locale.get(player, "city.plot.claimed"));
  }

  private void renew(Player player) {
    City city = plugin.cities().cityAt(player.getLocation());
    Plot plot = city == null
      ? null
      : plugin.plots().plotAt(city, player.getLocation());
    if (plot == null) {
      player.sendMessage(Locale.get(player, "city.plot.not_found"));
      return;
    }
    if (plugin.plots().renew(plot, player.getUniqueId())) {
      player.sendMessage(Locale.get(player, "city.rent.renewed"));
    } else {
      player.sendMessage(Locale.get(player, "city.rent.insufficient"));
    }
  }

  private void release(Player player) {
    City city = plugin.cities().cityAt(player.getLocation());
    Plot plot = city == null
      ? null
      : plugin.plots().plotAt(city, player.getLocation());
    if (plot == null) {
      player.sendMessage(Locale.get(player, "city.plot.not_found"));
      return;
    }
    if (plugin.plots().release(plot, player.getUniqueId())) {
      player.sendMessage(Locale.get(player, "city.plot.released"));
    } else {
      player.sendMessage(Locale.get(player, "city.plot.not_owner"));
    }
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    if (arguments.length <= 1) return SUBCOMMANDS;
    if (arguments.length == 2 && arguments[0].equalsIgnoreCase("spawn")) {
      return plugin.cities().all().stream().map(City::name).toList();
    }
    return List.of();
  }
}
