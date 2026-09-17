package dev.bisz.city.commands;

import dev.bisz.city.CityPlugin;
import dev.bisz.city.model.ChunkKey;
import dev.bisz.city.model.City;
import dev.bisz.city.model.ZoneType;
import dev.bisz.city.wilderness.RegionKey;
import dev.bisz.city.wilderness.RegionState;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Administrative diagnostics and forced regeneration for testing. */
public final class CityTestCommand extends DevCommand {

  private static final List<String> SUBCOMMANDS = List.of(
    "zone",
    "region",
    "force",
    "forceregion",
    "clear",
    "diag"
  );

  private final CityPlugin plugin;

  public CityTestCommand(CityPlugin plugin) {
    super("citytest", "city.admin");
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
      player.sendMessage(Locale.get(player, "city.test.help"));
      return true;
    }
    switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
      case "zone" -> zone(player);
      case "region" -> region(player);
      case "force" -> force(player, false);
      case "forceregion" -> force(player, true);
      case "clear" -> clear(player);
      case "diag" -> diag(player);
      default -> player.sendMessage(
        Locale.get(player, "city.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void zone(Player player) {
    ZoneType zone = plugin.cities().zoneAt(player.getLocation());
    player.sendMessage(
      Locale.get(
        player,
        "city.test.zone",
        zone == null ? "unmanaged" : zone.name()
      )
    );
  }

  private void region(Player player) {
    RegionKey key = regionOf(player);
    RegionState state = plugin.indicators().get(key);
    if (state == null) {
      player.sendMessage(Locale.get(player, "city.test.region.untracked"));
      return;
    }
    long ageSeconds =
      (System.currentTimeMillis() - state.lastRegenAt()) / 1000L;
    player.sendMessage(
      Locale.get(
        player,
        "city.test.region",
        key.encode(),
        String.format(java.util.Locale.ROOT, "%.2f", state.resource()),
        String.format(java.util.Locale.ROOT, "%.2f", state.visibility()),
        ageSeconds
      )
    );
  }

  private void force(Player player, boolean wholeRegion) {
    Location location = player.getLocation();
    if (wholeRegion) {
      int regenerated = plugin
        .regenerationScheduler()
        .forceRegion(regionOf(player));
      report(player, regenerated);
      return;
    }
    boolean ok = plugin
      .regenerationScheduler()
      .forceChunk(ChunkKey.of(location));
    report(player, ok ? 1 : 0);
  }

  private void report(Player player, int regenerated) {
    if (regenerated <= 0) {
      player.sendMessage(Locale.get(player, "city.test.force.fail"));
      return;
    }
    player.sendMessage(
      Locale.get(player, "city.test.force.ok", regenerated)
    );
  }

  private void clear(Player player) {
    RegionKey key = regionOf(player);
    plugin.indicators().remove(key);
    plugin.indicators().save();
    player.sendMessage(Locale.get(player, "city.test.clear", key.encode()));
  }

  private void diag(Player player) {
    int plots = 0;
    for (City city : plugin.cities().all()) {
      plots += plugin.plots().storedPlots(city).size();
    }
    ZoneType zone = plugin.cities().zoneAt(player.getLocation());
    player.sendMessage("§8§m----------§r §eCity diagnostics §8§m----------");
    player.sendMessage("§7Zone: §f" + (zone == null ? "unmanaged" : zone));
    player.sendMessage(
      "§7WorldEdit regen: §f" + plugin.regenerator().name()
    );
    player.sendMessage(
      "§7Structure backend: §f" + plugin.structures().bridge().name()
    );
    player.sendMessage(
      "§7Currency available: §f" + plugin.currency().isAvailable()
    );
    player.sendMessage(
      "§7Cities: §f" +
      plugin.cities().all().size() +
      "§7, plots: §f" +
      plots
    );
    player.sendMessage(
      "§7Monuments: §f" +
      plugin.monuments().all().size() +
      "§7, structure instances: §f" +
      plugin.structures().instances().size()
    );
    player.sendMessage(
      "§7Tracked regions: §f" + plugin.indicators().all().size()
    );
    player.sendMessage(
      "§7Managed worlds: §f" +
      (plugin.settings().managedWorlds().isEmpty()
        ? "all"
        : String.join(", ", plugin.settings().managedWorlds()))
    );
  }

  private RegionKey regionOf(Player player) {
    return RegionKey.of(
      ChunkKey.of(player.getLocation()),
      plugin.settings().regionSize()
    );
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    return SUBCOMMANDS;
  }
}
