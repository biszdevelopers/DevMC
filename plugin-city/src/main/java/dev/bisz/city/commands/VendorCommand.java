package dev.bisz.city.commands;

import dev.bisz.city.CityPlugin;
import dev.bisz.city.economy.SystemVendor;
import dev.bisz.city.menus.VendorMenu;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** The one-way system vendor: sells anything except contraband. */
public final class VendorCommand extends DevCommand {

  private final CityPlugin plugin;

  public VendorCommand(CityPlugin plugin) {
    super("vendor", "city.vendor");
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
    if (!plugin.currency().isAvailable()) {
      player.sendMessage(Locale.get(player, "city.vendor.unavailable"));
      return true;
    }
    if (arguments.length == 0) {
      VendorMenu.open(plugin, player);
      return true;
    }
    switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
      case "sell" -> report(player, plugin.vendor().sellHand(player));
      case "all" -> report(player, plugin.vendor().sellInventory(player));
      default -> player.sendMessage(
        Locale.get(player, "city.command.unknown", arguments[0])
      );
    }
    return true;
  }

  /** Sends a localized summary of a vendor transaction. */
  public static void report(Player player, SystemVendor.SellResult result) {
    if (result.contrabandRefused() > 0) {
      player.sendMessage(Locale.get(player, "city.vendor.contraband"));
    }
    if (result.quotaReached()) {
      player.sendMessage(Locale.get(player, "city.vendor.quota"));
    }
    if (result.itemsSold() == 0 && result.contrabandRefused() == 0) {
      if (!result.quotaReached()) {
        player.sendMessage(Locale.get(player, "city.vendor.nothing"));
      }
      return;
    }
    if (result.itemsSold() > 0) {
      player.sendMessage(
        Locale.get(
          player,
          "city.vendor.sold",
          result.itemsSold(),
          result.nitsPaid()
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
    return List.of("sell", "all");
  }
}
