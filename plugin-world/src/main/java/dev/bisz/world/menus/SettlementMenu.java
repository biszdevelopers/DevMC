package dev.bisz.world.menus;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.Settlement;
import dev.bisz.menus.MenuItem;
import dev.bisz.menus.SinglePageMenuTemplate;
import dev.bisz.players.locales.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Lists every settlement and teleports on click. */
public final class SettlementMenu {

  private SettlementMenu() {}

  /** Opens the settlement browser for a player. */
  public static void open(WorldPlugin plugin, Player player) {
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      Locale.get(player, "settlement.menu.settlements.title"),
      6
    );
    int slot = 0;
    for (Settlement settlement : plugin.settlements().all()) {
      if (slot >= 45) break;
      builder.item(
        slot,
        MenuItem.builder(Material.GRASS_BLOCK)
          .name(settlement.name())
          .lore(
            Locale.get(player, "settlement.menu.settlements.chunks", settlement.chunks().size()),
            Locale.get(player, "settlement.menu.settlements.rent", settlement.rentPrice())
          )
          .onClick(context -> teleport(context.player(), settlement))
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

  private static void teleport(Player player, Settlement settlement) {
    Location spawn = settlement.spawn();
    if (spawn == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.spawn.unset"));
      return;
    }
    player.teleport(spawn);
    player.sendMessage(Locale.get(player, "settlement.settlement.spawn.teleport", settlement.name()));
  }
}
