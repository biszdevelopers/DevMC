package dev.bisz.world.commands;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.integration.BlockRegion;
import dev.bisz.world.integration.SelectionRegion;
import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.model.Monument;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.ZoneType;
import dev.bisz.world.structure.StructureDefinition;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Administrative world, settlement, monument, and POI management. */
public final class WorldAdminCommand extends DevCommand {

  private static final int MAX_SELECTION_CHUNKS = 4096;
  private static final int DEFAULT_MONUMENT_RADIUS = 16;

  private static final List<String> SUBCOMMANDS = List.of(
    "reload",
    "diag",
    "pregen",
    "cycle",
    "regenerate",
    "clear",
    "hud",
    "create",
    "delete",
    "addchunk",
    "removechunk",
    "setspawn",
    "setrent",
    "setperiod",
    "setplotsize",
    "monument",
    "poi"
  );

  private final WorldPlugin plugin;

  public WorldAdminCommand(WorldPlugin plugin) {
    super("worldadmin", "world.admin");
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
      help(player);
      return true;
    }
    switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
      case "reload" -> reload(player);
      case "diag" -> diag(player);
      case "pregen" -> pregen(player, arguments);
      case "cycle" -> cycle(player, arguments);
      case "regenerate" -> regenerate(player, arguments);
      case "clear" -> clear(player);
      case "hud" -> hud(player);
      case "create" -> create(player, arguments);
      case "delete" -> delete(player, arguments);
      case "addchunk" -> addChunk(player, arguments);
      case "removechunk" -> removeChunk(player, arguments);
      case "setspawn" -> setSpawn(player, arguments);
      case "setrent" -> setRent(player, arguments);
      case "setperiod" -> setPeriod(player, arguments);
      case "setplotsize" -> setPlotSize(player, arguments);
      case "monument" -> monument(player, arguments);
      case "poi" -> poi(player, arguments);
      default -> player.sendMessage(
        Locale.get(player, "settlement.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void help(Player player) {
    player.sendMessage("§8§m------§r §e/worldadmin §8§m------");
    player.sendMessage("§freload §7- reload settlements, plots, monuments, indicators");
    player.sendMessage("§fdiag §7- backends, counts, and status");
    player.sendMessage("§fpregen [radius] §7- generate the whole island (default)");
    player.sendMessage("§fcycle [seconds|off] §7- override the regen cycle (testing)");
    player.sendMessage("§fregenerate [radius] §7- regenerate the current chunk or an area");
    player.sendMessage("§fclear §7- clear the current chunk's indicators");
    player.sendMessage("§fhud §7- toggle the indicator HUD");
    player.sendMessage("§fcreate|delete|addchunk|removechunk §7- settlements");
    player.sendMessage("§fsetspawn|setrent|setperiod|setplotsize §7- settlement tuning");
    player.sendMessage("§fmonument <add|remove|list> §7- admin-defined landmarks");
    player.sendMessage("§fpoi <list|reload> §7- stored POI schematics");
  }

  private void reload(Player player) {
    plugin.reloadData();
    plugin.pois().reload();
    player.sendMessage(Locale.get(player, "settlement.reload"));
  }

  private void diag(Player player) {
    int plots = 0;
    for (Settlement settlement : plugin.settlements().all()) {
      plots += plugin.plots().storedPlots(settlement).size();
    }
    ZoneType zone = plugin.settlements().zoneAt(player.getLocation());
    player.sendMessage("§8§m----------§r §eWorld diagnostics §8§m----------");
    player.sendMessage("§7Managed world: §f" + plugin.setup().managedWorldName());
    player.sendMessage("§7Zone: §f" + (zone == null ? "unmanaged" : zone));
    player.sendMessage("§7WorldEdit regen: §f" + plugin.regenerator().name());
    player.sendMessage(
      "§7Structure backend: §f" + plugin.pois().bridge().name()
    );
    player.sendMessage(
      "§7Currency available: §f" + plugin.currency().isAvailable()
    );
    player.sendMessage(
      "§7Settlements: §f" +
      plugin.settlements().all().size() +
      "§7, plots: §f" +
      plots
    );
    player.sendMessage(
      "§7Monuments: §f" +
      plugin.monuments().all().size() +
      "§7, POI definitions: §f" +
      plugin.pois().definitions().size() +
      "§7, live POIs: §f" +
      plugin.pois().liveCount()
    );
    player.sendMessage(
      "§7Auto-regen: §f" + plugin.regenerationScheduler().status()
    );
    String here = plugin
      .regenerationScheduler()
      .chunkStatus(dev.bisz.world.model.ChunkKey.of(player.getLocation()));
    player.sendMessage(
      "§7This chunk: §f" +
      (zone == null ? "unmanaged" : zone) +
      " §7" +
      (here == null ? "untracked" : here)
    );
    if (zone == ZoneType.POLICED || zone == ZoneType.UNMONITORED) {
      player.sendMessage(
        "§8(settlements never regenerate; farm the wilderness)"
      );
    }
    player.sendMessage(
      "§7Pregen/area: §f" +
      (plugin.areaRegenerator().running()
        ? plugin.areaRegenerator().done() +
          "/" +
          plugin.areaRegenerator().total()
        : "idle §8(normal when not pregenerating)")
    );
  }

  /**
   * Generates a square of chunks around the player without resetting terrain.
   * Defaults to the whole island. Usage: /worldadmin pregen [radius]
   */
  private void pregen(Player player, String[] arguments) {
    World world = plugin.setup().ensureWorld();
    if (world == null) {
      player.sendMessage("§cThe managed world is unavailable.");
      return;
    }
    if (arguments.length > 1 && arguments[1].equalsIgnoreCase("stop")) {
      plugin.areaRegenerator().stop();
      player.sendMessage("§ePre-generation stopped.");
      return;
    }
    int radius = arguments.length > 1
      ? (int) parseLong(arguments[1], defaultPregenRadius())
      : defaultPregenRadius();
    if (radius <= 0) radius = defaultPregenRadius();
    plugin
      .areaRegenerator()
      .startGenerate(world, player.getLocation(), radius, player);
    player.sendMessage(
      "§aPre-generating a radius of §f" +
      radius +
      "§a chunks (§f" +
      (2 * radius + 1) * (2 * radius + 1) +
      "§a total)."
    );
  }

  private int defaultPregenRadius() {
    return Math.max(1, plugin.settings().islandSize() / 2 / 16);
  }

  /**
   * Overrides the reset cycle at runtime for testing. Usage:
   * /worldadmin cycle [seconds|off]
   */
  private void cycle(Player player, String[] arguments) {
    if (arguments.length < 2 || arguments[1].equalsIgnoreCase("off")) {
      int rescheduled = plugin.regenerationScheduler().setCycleOverride(0L);
      player.sendMessage(
        "§eRegen cycle override cleared (§f" +
        plugin.settings().regenCycleMillis() / 1000L +
        "s§e); rescheduled §f" +
        rescheduled +
        "§e chunk(s)."
      );
      return;
    }
    int seconds = (int) parseLong(arguments[1], -1L);
    if (seconds <= 0) {
      int rescheduled = plugin.regenerationScheduler().setCycleOverride(0L);
      player.sendMessage(
        "§eRegen cycle override cleared; rescheduled §f" +
        rescheduled +
        "§e chunk(s)."
      );
      return;
    }
    int rescheduled = plugin.regenerationScheduler().setCycleOverride(
      seconds * 1000L
    );
    player.sendMessage(
      "§aRegen cycle = §f" +
      seconds +
      "s§a (test); rescheduled §f" +
      rescheduled +
      "§a chunk(s)."
    );
  }

  /**
   * Regenerates the current chunk, or a square of chunks when a radius is given.
   * Regeneration resets terrain and re-applies features.
   */
  private void regenerate(Player player, String[] arguments) {
    if (arguments.length > 1 && arguments[1].equalsIgnoreCase("stop")) {
      plugin.areaRegenerator().stop();
      player.sendMessage("§eRegeneration stopped.");
      return;
    }
    int radius = arguments.length > 1
      ? (int) parseLong(arguments[1], 0L)
      : 0;
    if (radius <= 0) {
      boolean ok = plugin
        .regenerationScheduler()
        .forceChunk(ChunkKey.of(player.getLocation()));
      player.sendMessage(
        ok
          ? "§aRegenerated the current chunk."
          : "§cRegeneration failed (backend unavailable?)."
      );
      return;
    }
    World world = plugin.setup().ensureWorld();
    if (world == null) {
      player.sendMessage("§cThe managed world is unavailable.");
      return;
    }
    plugin
      .areaRegenerator()
      .startRegenerate(world, player.getLocation(), radius, player);
    player.sendMessage("§aRegenerating a radius of §f" + radius + "§a chunks.");
  }

  private void clear(Player player) {
    ChunkKey key = ChunkKey.of(player.getLocation());
    plugin.indicators().remove(key);
    plugin.indicators().save();
    player.sendMessage("§aCleared indicators for §f" + key.encode() + "§a.");
  }

  private void hud(Player player) {
    boolean on = plugin.indicatorHud().toggle(player);
    player.sendMessage(
      on ? "§aIndicator HUD enabled." : "§eIndicator HUD disabled."
    );
  }

  private void create(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/worldadmin create <name>")
      );
      return;
    }
    Settlement settlement = plugin
      .settlements()
      .create(arguments[1], player.getLocation());
    SelectionRegion selection = plugin.selection().selection(player);
    int added = 0;
    if (selection != null) {
      if (selection.chunkCount() > MAX_SELECTION_CHUNKS) {
        player.sendMessage(
          "§eSelection is large (" +
          selection.chunkCount() +
          " chunks); adding only the first " +
          MAX_SELECTION_CHUNKS +
          "."
        );
      }
      for (int cx = selection.minChunkX(); cx <= selection.maxChunkX(); cx++) {
        for (
          int cz = selection.minChunkZ();
          cz <= selection.maxChunkZ();
          cz++
        ) {
          if (added >= MAX_SELECTION_CHUNKS) break;
          if (
            plugin
              .settlements()
              .addChunk(settlement, new ChunkKey(selection.world(), cx, cz))
          ) {
            added++;
          }
        }
        if (added >= MAX_SELECTION_CHUNKS) break;
      }
    }
    player.sendMessage(
      Locale.get(player, "settlement.settlement.created", settlement.name())
    );
    if (added > 0) {
      player.sendMessage(
        "§aAdded §f" + added + "§a chunk(s) from the WorldEdit selection."
      );
    } else if (selection == null) {
      player.sendMessage(
        "§7No WorldEdit selection; created a single-chunk settlement. " +
        "Make a selection and re-run create, or use /worldadmin addchunk."
      );
    }
  }

  private void delete(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/worldadmin delete <name>")
      );
      return;
    }
    Settlement settlement = plugin.settlements().byName(arguments[1]);
    if (settlement == null || !plugin.settlements().delete(settlement.id())) {
      player.sendMessage(Locale.get(player, "settlement.settlement.not_found"));
      return;
    }
    player.sendMessage(
      Locale.get(player, "settlement.settlement.deleted", settlement.name())
    );
  }

  private void addChunk(Player player, String[] arguments) {
    Settlement settlement = resolveSettlement(player, arguments);
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.not_found"));
      return;
    }
    if (
      plugin.settlements().addChunk(settlement, ChunkKey.of(player.getLocation()))
    ) {
      player.sendMessage(
        Locale.get(player, "settlement.chunk.added", settlement.name())
      );
    } else {
      player.sendMessage(
        Locale.get(player, "settlement.chunk.already", settlement.name())
      );
    }
  }

  private void removeChunk(Player player, String[] arguments) {
    Settlement settlement = resolveSettlement(player, arguments);
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.not_found"));
      return;
    }
    if (
      plugin
        .settlements()
        .removeChunk(settlement, ChunkKey.of(player.getLocation()))
    ) {
      player.sendMessage(
        Locale.get(player, "settlement.chunk.removed", settlement.name())
      );
    } else {
      player.sendMessage(
        Locale.get(player, "settlement.chunk.not_present", settlement.name())
      );
    }
  }

  private void setSpawn(Player player, String[] arguments) {
    Settlement settlement = resolveSettlement(player, arguments);
    if (settlement == null) {
      player.sendMessage(Locale.get(player, "settlement.settlement.not_found"));
      return;
    }
    settlement.spawn(player.getLocation());
    plugin.settlements().save();
    player.sendMessage(
      Locale.get(player, "settlement.settlement.spawn.set", settlement.name())
    );
  }

  private void setRent(Player player, String[] arguments) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null || arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/worldadmin setrent <nits>")
      );
      return;
    }
    settlement.rentPrice(parseLong(arguments[1], settlement.rentPrice()));
    plugin.settlements().save();
    player.sendMessage(
      Locale.get(player, "settlement.settlement.rent.set", settlement.rentPrice())
    );
  }

  private void setPeriod(Player player, String[] arguments) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null || arguments.length < 2) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin setperiod <seconds>"
        )
      );
      return;
    }
    long seconds = parseLong(arguments[1], settlement.rentPeriodMillis() / 1000L);
    settlement.rentPeriodMillis(seconds * 1000L);
    plugin.settlements().save();
    player.sendMessage(
      Locale.get(player, "settlement.settlement.period.set", seconds)
    );
  }

  private void setPlotSize(Player player, String[] arguments) {
    Settlement settlement = plugin.settlements().settlementAt(player.getLocation());
    if (settlement == null || arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/worldadmin setplotsize <size>")
      );
      return;
    }
    int size = (int) parseLong(arguments[1], settlement.plotSize());
    settlement.plotSize(size);
    plugin.settlements().save();
    player.sendMessage(
      Locale.get(player, "settlement.settlement.plotsize.set", settlement.plotSize())
    );
  }

  private void monument(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin monument <add|remove|list>"
        )
      );
      return;
    }
    switch (arguments[1].toLowerCase(java.util.Locale.ROOT)) {
      case "add" -> monumentAdd(player, arguments);
      case "remove" -> monumentRemove(player, arguments);
      case "list" -> monumentList(player);
      default -> player.sendMessage(
        Locale.get(player, "settlement.command.unknown", arguments[1])
      );
    }
  }

  private void monumentAdd(Player player, String[] arguments) {
    if (arguments.length < 3) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin monument add <id> [tier] [table]"
        )
      );
      return;
    }
    String id = arguments[2];
    int tier = arguments.length > 3
      ? (int) parseLong(arguments[3], 1L)
      : 1;
    String table = arguments.length > 4 ? arguments[4] : "tier" + tier;

    BlockRegion selection = plugin.selection().blockSelection(player);
    int minX;
    int minY;
    int minZ;
    int maxX;
    int maxY;
    int maxZ;
    String world;
    if (selection != null) {
      world = selection.world();
      minX = selection.minX();
      minY = selection.minY();
      minZ = selection.minZ();
      maxX = selection.maxX();
      maxY = selection.maxY();
      maxZ = selection.maxZ();
    } else {
      var location = player.getLocation();
      world = location.getWorld().getName();
      minX = location.getBlockX() - DEFAULT_MONUMENT_RADIUS;
      minY = location.getBlockY() - DEFAULT_MONUMENT_RADIUS;
      minZ = location.getBlockZ() - DEFAULT_MONUMENT_RADIUS;
      maxX = location.getBlockX() + DEFAULT_MONUMENT_RADIUS;
      maxY = location.getBlockY() + DEFAULT_MONUMENT_RADIUS;
      maxZ = location.getBlockZ() + DEFAULT_MONUMENT_RADIUS;
    }
    Monument monument = new Monument(
      id,
      world,
      minX,
      minY,
      minZ,
      maxX,
      maxY,
      maxZ,
      tier,
      table,
      Math.max(1L, tier) * 36_000L,
      tier * 0.5,
      null
    );
    plugin.monuments().add(monument);
    player.sendMessage(
      "§aMonument §f" +
      id +
      "§a defined" +
      (selection != null ? " from your selection." : " (radius " + DEFAULT_MONUMENT_RADIUS + ").")
    );
  }

  private void monumentRemove(Player player, String[] arguments) {
    if (arguments.length < 3) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.command.usage",
          "/worldadmin monument remove <id>"
        )
      );
      return;
    }
    if (plugin.monuments().remove(arguments[2])) {
      player.sendMessage(
        Locale.get(player, "settlement.monument.removed", arguments[2])
      );
    } else {
      player.sendMessage(Locale.get(player, "settlement.monument.none"));
    }
  }

  private void monumentList(Player player) {
    if (plugin.monuments().all().isEmpty()) {
      player.sendMessage("§7No monuments defined.");
      return;
    }
    for (Monument monument : plugin.monuments().all()) {
      player.sendMessage(
        Locale.get(
          player,
          "settlement.monument.entry",
          monument.id(),
          monument.tier(),
          monument.lootTableId()
        )
      );
    }
  }

  private void poi(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "settlement.command.usage", "/worldadmin poi <list|reload>")
      );
      return;
    }
    switch (arguments[1].toLowerCase(java.util.Locale.ROOT)) {
      case "reload" -> {
        plugin.pois().reload();
        player.sendMessage("§aPOI definitions reloaded.");
      }
      case "list" -> {
        if (plugin.pois().definitions().isEmpty()) {
          player.sendMessage("§7No POI definitions stored.");
          return;
        }
        for (StructureDefinition definition : plugin.pois().definitions()) {
          player.sendMessage(
            "§7- §f" +
            definition.id() +
            " §8[" +
            definition.category() +
            "] §7" +
            definition.file()
          );
        }
      }
      default -> player.sendMessage(
        Locale.get(player, "settlement.command.unknown", arguments[1])
      );
    }
  }

  private Settlement resolveSettlement(Player player, String[] arguments) {
    if (arguments.length > 1) {
      Settlement named = plugin.settlements().byName(arguments[1]);
      if (named != null) return named;
    }
    return plugin.settlements().settlementAt(player.getLocation());
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
      if (arguments[0].equalsIgnoreCase("poi")) {
        return List.of("list", "reload");
      }
      if (arguments[0].equalsIgnoreCase("regenerate")) {
        return List.of("stop");
      }
      return plugin.settlements().all().stream().map(Settlement::name).toList();
    }
    return List.of();
  }
}
