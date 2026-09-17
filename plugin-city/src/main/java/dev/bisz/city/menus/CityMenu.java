package dev.bisz.city.menus;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.city.CityPlugin;
import dev.bisz.city.model.City;
import dev.bisz.menus.MenuItem;
import dev.bisz.menus.SinglePageMenuTemplate;
import dev.bisz.players.locales.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Lists every city and teleports on click. */
public final class CityMenu {

  private CityMenu() {}

  /** Opens the city browser for a player. */
  public static void open(CityPlugin plugin, Player player) {
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      Locale.get(player, "city.menu.cities.title"),
      6
    );
    int slot = 0;
    for (City city : plugin.cities().all()) {
      if (slot >= 45) break;
      builder.item(
        slot,
        MenuItem.builder(Material.GRASS_BLOCK)
          .name(city.name())
          .lore(
            Locale.get(player, "city.menu.cities.chunks", city.chunks().size()),
            Locale.get(player, "city.menu.cities.rent", city.rentPrice())
          )
          .onClick(context -> teleport(context.player(), city))
          .build()
      );
      slot++;
    }
    if (slot == 0) {
      builder.item(
        22,
        MenuItem.builder(Material.BARRIER)
          .name(Locale.get(player, "city.menu.empty"))
          .build()
      );
    }
    BundlerPlugin.instance().menuManager().open(player, builder.build());
  }

  private static void teleport(Player player, City city) {
    Location spawn = city.spawn();
    if (spawn == null) {
      player.sendMessage(Locale.get(player, "city.city.spawn.unset"));
      return;
    }
    player.teleport(spawn);
    player.sendMessage(Locale.get(player, "city.city.spawn.teleport", city.name()));
  }
}
