package dev.bisz.city.menus;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.city.CityPlugin;
import dev.bisz.city.commands.VendorCommand;
import dev.bisz.menus.MenuItem;
import dev.bisz.menus.SinglePageMenuTemplate;
import dev.bisz.players.locales.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** The system vendor menu: sell hand or sell everything. */
public final class VendorMenu {

  private VendorMenu() {}

  /** Opens the vendor menu for a player. */
  public static void open(CityPlugin plugin, Player player) {
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      Locale.get(player, "city.menu.vendor.title"),
      3
    );
    builder.item(
      11,
      MenuItem.builder(Material.HOPPER)
        .name(Locale.get(player, "city.menu.vendor.sell_all"))
        .onClick(context -> {
          VendorCommand.report(
            context.player(),
            plugin.vendor().sellInventory(context.player())
          );
          context.session().refresh();
        })
        .build()
    );
    builder.item(
      15,
      MenuItem.builder(Material.DIAMOND)
        .name(Locale.get(player, "city.menu.vendor.sell_hand"))
        .onClick(context -> {
          VendorCommand.report(
            context.player(),
            plugin.vendor().sellHand(context.player())
          );
          context.session().refresh();
        })
        .build()
    );
    builder.item(
      13,
      MenuItem.builder(Material.GOLD_INGOT)
        .name(
          player1 ->
            Locale.get(
              player1,
              "city.menu.vendor.balance",
              plugin.currency().balance(player1.getUniqueId())
            )
        )
        .build()
    );
    BundlerPlugin.instance().menuManager().open(player, builder.build());
  }
}
