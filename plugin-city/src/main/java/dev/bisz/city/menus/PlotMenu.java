package dev.bisz.city.menus;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.city.CityPlugin;
import dev.bisz.city.model.City;
import dev.bisz.city.model.Plot;
import dev.bisz.city.model.PlotId;
import dev.bisz.city.model.PlotState;
import dev.bisz.menus.MenuItem;
import dev.bisz.menus.SinglePageMenuTemplate;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Browses and claims the plots of the city the player stands in. */
public final class PlotMenu {

  private PlotMenu() {}

  /** Opens the plot browser for the city at the player's location. */
  public static void open(CityPlugin plugin, Player player) {
    City city = plugin.cities().cityAt(player.getLocation());
    if (city == null) {
      player.sendMessage(Locale.get(player, "city.city.none"));
      return;
    }
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      Locale.get(player, "city.menu.plots.title", city.name()),
      6
    );
    List<PlotId> ids = plugin.plots().plotIds(city);
    int slot = 0;
    for (PlotId id : ids) {
      if (slot >= 45) break;
      Plot plot = plugin.plots().get(id);
      PlotState state = plot == null ? PlotState.VACANT : plot.state();
      boolean owned =
        plot != null && plot.owner() != null && plot.owner().equals(player.getUniqueId());
      builder.item(
        slot,
        MenuItem.builder(materialFor(state, owned))
          .name(
            Locale.get(
              player,
              "city.menu.plots.entry",
              id.gridX(),
              id.gridZ()
            )
          )
          .lore(Locale.get(player, stateKey(state)))
          .onClick(context -> act(plugin, context.player(), city, id))
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

  private static void act(
    CityPlugin plugin,
    Player player,
    City city,
    PlotId id
  ) {
    Plot plot = plugin.plots().get(id);
    if (plot != null && plot.owner() != null && plot.owner().equals(player.getUniqueId())) {
      if (plugin.plots().renew(plot, player.getUniqueId())) {
        player.sendMessage(Locale.get(player, "city.rent.renewed"));
      } else {
        player.sendMessage(Locale.get(player, "city.rent.insufficient"));
      }
      return;
    }
    if (plugin.plots().isClaimable(plot)) {
      Plot claimed = plugin.plots().claim(city, id, player.getUniqueId());
      player.sendMessage(
        Locale.get(
          player,
          claimed == null ? "city.plot.unavailable" : "city.plot.claimed"
        )
      );
      return;
    }
    player.sendMessage(Locale.get(player, "city.plot.unavailable"));
  }

  private static Material materialFor(PlotState state, boolean owned) {
    return switch (state) {
      case VACANT, DERELICT, RAIDABLE -> Material.LIME_CONCRETE;
      case RENTED -> owned ? Material.DIAMOND_BLOCK : Material.RED_CONCRETE;
    };
  }

  private static String stateKey(PlotState state) {
    return "city.plot.state." + state.name().toLowerCase(java.util.Locale.ROOT);
  }
}
