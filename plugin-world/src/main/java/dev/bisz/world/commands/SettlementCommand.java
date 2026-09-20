package dev.bisz.world.commands;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.Plot;
import dev.bisz.world.model.PlotId;
import dev.bisz.world.plot.PlotGrid;
import dev.bisz.world.menus.SettlementMenu;
import dev.bisz.world.menus.PlotMenu;
import dev.bisz.world.menus.VendorMenu;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Player-facing settlement and plot command. */
public final class SettlementCommand extends DevCommand {

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

  private final WorldPlugin plugin;

  public SettlementCommand(WorldPlugin plugin) {
    super("settlement", "world.use");
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
      SettlementMenu.open(plugin, player);
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
        Locale.get(player, "settlement.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void list(Player player) {
    player.sendMessage(
      Locale.get(player, "settlement.command.list.header", plugin.settlements().all().size())
    );
    for (Settlement settlement : plugin.settlements().all()) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.list.entry",
          settlement.name(),
          settlement.chunks().size(),
          settlement.rentPrice()
        )
      );
    }
  }

  private void info(Player player) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.none"));
      return;
    }
    player.sendMessage(
      Locale.get(
        player,
        "settlement.settlement.info",
        settlement.name(),
        settlement.chunks().size(),
        settlement.rentPrice()
      )
    );
  }

  private void spawn(Player player, String[] arguments) {
    Settlement settlement = arguments.length > 1
      ? plugin.settlements().byName(arguments[1])
      : plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.not_found"));
      return;
    }
    Location spawn = settlement.spawn();
    if (spawn == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.spawn.unset"));
      return;
    }
    player.teleport(spawn);
    player.sendMessage(Locale.get(player, "settlement.settlement.spawn.teleport", settlement.name()));
  }

  private void claim(Player player) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.none"));
      return;
    }
    PlotId id = PlotGrid.plotAt(
      settlement,
      player.getLocation().getBlockX(),
      player.getLocation().getBlockZ(),
      0
    );
    Plot plot = plugin.plots().claim(settlement, id, player.getUniqueId());
    if (plot == null) {
      player.sendMessage(Locale.get(player, "settlement.plot.unavailable"));
      return;
    }
    player.sendMessage(Locale.get(player, "settlement.plot.claimed"));
  }

  private void renew(Player player) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    Plot plot = settlement == null
      ? null
      : plugin.plots().plotAt(settlement, player.getLocation());
    if (plot == null) {
      player.sendMessage(Locale.get(player, "settlement.plot.not_found"));
      return;
    }
    if (plugin.plots().renew(plot, player.getUniqueId())) {
      player.sendMessage(Locale.get(player, "settlement.rent.renewed"));
    } else {
      player.sendMessage(Locale.get(player, "settlement.rent.insufficient"));
    }
  }

  private void release(Player player) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    Plot plot = settlement == null
      ? null
      : plugin.plots().plotAt(settlement, player.getLocation());
    if (plot == null) {
      player.sendMessage(Locale.get(player, "settlement.plot.not_found"));
      return;
    }
    if (plugin.plots().release(plot, player.getUniqueId())) {
      player.sendMessage(Locale.get(player, "settlement.plot.released"));
    } else {
      player.sendMessage(Locale.get(player, "settlement.plot.not_owner"));
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
      return plugin.settlements().all().stream().map(Settlement::name).toList();
    }
    return List.of();
  }
}
