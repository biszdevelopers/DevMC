package dev.bisz.world.menus;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.Plot;
import dev.bisz.world.model.PlotId;
import dev.bisz.world.model.PlotState;
import dev.bisz.menus.MenuItem;
import dev.bisz.menus.SinglePageMenuTemplate;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Browses and claims the plots of the settlement the player stands in. */
public final class PlotMenu {

  private PlotMenu() {}

  /** Opens the plot browser for the settlement at the player's location. */
  public static void open(WorldPlugin plugin, Player player) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.none"));
      return;
    }
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      Locale.get(player, "settlement.menu.plots.title", settlement.name()),
      6
    );
    List<PlotId> ids = plugin.plots().plotIds(settlement);
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
              "settlement.menu.plots.entry",
              id.gridX(),
              id.gridZ()
            )
          )
          .lore(Locale.get(player, stateKey(state)))
          .onClick(context -> act(plugin, context.player(), settlement, id))
          .build()
      );
      slot++;
    }
    if (slot == 0) {
      builder.item(
        22,
        MenuItem.builder(Material.BARRIER)
          .name(Locale.get(player, "settlement.menu.empty"))
          .build()
      );
    }
    BundlerPlugin.instance().menuManager().open(player, builder.build());
  }

  private static void act(
    WorldPlugin plugin,
    Player player,
    Settlement settlement,
    PlotId id
  ) {
    Plot plot = plugin.plots().get(id);
    if (plot != null && plot.owner() != null && plot.owner().equals(player.getUniqueId())) {
      if (plugin.plots().renew(plot, player.getUniqueId())) {
        player.sendMessage(Locale.get(player, "settlement.rent.renewed"));
      } else {
        player.sendMessage(Locale.get(player, "settlement.rent.insufficient"));
      }
      return;
    }
    if (plugin.plots().isClaimable(plot)) {
      Plot claimed = plugin.plots().claim(settlement, id, player.getUniqueId());
      player.sendMessage(
        Locale.get(
          player,
          claimed == null ? "settlement.plot.unavailable" : "settlement.plot.claimed"
        )
      );
      return;
    }
    player.sendMessage(Locale.get(player, "settlement.plot.unavailable"));
  }

  private static Material materialFor(PlotState state, boolean owned) {
    return switch (state) {
      case VACANT, DERELICT, RAIDABLE -> Material.LIME_CONCRETE;
      case RENTED -> owned ? Material.DIAMOND_BLOCK : Material.RED_CONCRETE;
    };
  }

  private static String stateKey(PlotState state) {
    return "settlement.plot.state." + state.name().toLowerCase(java.util.Locale.ROOT);
  }
}
