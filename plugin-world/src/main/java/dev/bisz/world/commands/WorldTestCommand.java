package dev.bisz.world.commands;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.ZoneType;
import dev.bisz.world.sim.RegenerationSimulator;
import dev.bisz.world.snapshot.ChunkSnapshot;
import dev.bisz.world.wilderness.ChunkState;
import dev.bisz.world.worldgen.RustMapGenerator;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Administrative diagnostics, snapshots, and forced regeneration for testing. */
public final class WorldTestCommand extends DevCommand {

  private static final List<String> SUBCOMMANDS = List.of(
    "zone",
    "chunk",
    "indicators",
    "force",
    "clear",
    "capture",
    "simulate",
    "snapshots",
    "admin",
    "world",
    "structure",
    "diag"
  );

  private final WorldPlugin plugin;

  public WorldTestCommand(WorldPlugin plugin) {
    super("worldtest", "world.admin");
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
      case "zone" -> zone(player);
      case "chunk" -> chunk(player);
      case "indicators" -> indicators(player);
      case "force" -> force(player);
      case "clear" -> clear(player);
      case "capture" -> capture(player, arguments);
      case "simulate" -> simulate(player, arguments);
      case "snapshots" -> snapshots(player);
      case "admin" -> admin(player);
      case "world" -> world(player, arguments);
      case "structure" -> structure(player, arguments);
      case "diag" -> diag(player);
      default -> player.sendMessage(
        Locale.get(player, "settlement.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void help(Player player) {
    player.sendMessage("§8§m------§r §e/worldtest §8§m------");
    player.sendMessage("§fchunk §7- chunk index and indicators");
    player.sendMessage("§findicators §7- toggle the live indicator HUD");
    player.sendMessage("§fsimulate [snapshot] [cycles] §7- regen simulation and view");
    player.sendMessage("§fcapture [name] §7- store the current chunk's blockstates");
    player.sendMessage("§fsnapshots §7- list stored snapshots");
    player.sendMessage("§fadmin §7- teleport to the storage dimension");
    player.sendMessage("§fworld [name] §7- create/enter a rust-map test world");
    player.sendMessage("§fstructure <id> §7- place a vanilla structure");
    player.sendMessage("§fforce §7/ §fclear §7- regeneration controls");
    player.sendMessage("§fzone §7/ §fdiag §7- diagnostics");
  }

  private void indicators(Player player) {
    boolean on = plugin.indicatorHud().toggle(player);
    player.sendMessage(on ? "§aIndicator HUD enabled." : "§eIndicator HUD disabled.");
  }

  /** Creates or enters a rust-map world with real terrain. */
  private void world(Player player, String[] arguments) {
    String name = arguments.length > 1 ? arguments[1] : "world_rustmap";
    World world = Bukkit.getWorld(name);
    if (world == null) {
      world = new WorldCreator(name)
        .environment(World.Environment.NORMAL)
        .generateStructures(false)
        .generator(
          new RustMapGenerator(
            plugin.settings().rustMapIslandRadius(),
            plugin.settings().rustMapSeaLevel(),
            plugin.settings().rustMapBiomes()
          )
        )
        .createWorld();
    }
    if (world == null) {
      player.sendMessage("§cCould not create the test world.");
      return;
    }
    player.teleport(
      new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 2, 0.5)
    );
    player.sendMessage(
      "§aTest world §f" + world.getName() + "§a ready (rust-map island)."
    );
  }

  private void zone(Player player) {
    ZoneType zone = plugin.settlements().zoneAt(player.getLocation());
    player.sendMessage(
      Locale.get(
        player,
        "settlement.test.zone",
        zone == null ? "unmanaged" : zone.name()
      )
    );
  }

  /** Prints the chunk index and its regeneration indicators. */
  private void chunk(Player player) {
    ChunkKey chunk = ChunkKey.of(player.getLocation());
    ChunkState state = plugin.indicators().get(chunk);

    player.sendMessage("§8§m------§r §eChunk index §8§m------");
    player.sendMessage("§7World: §f" + chunk.world());
    player.sendMessage("§7Chunk: §f" + chunk.x() + "§7, §f" + chunk.z());
    player.sendMessage(
      "§7Settlement chunk: §f" +
      plugin.settlements().isSettlement(chunk) +
      "§7, monument: §f" +
      plugin.monuments().isExempt(chunk)
    );
    if (state == null) {
      player.sendMessage("§7Indicators: §8untracked");
    } else {
      player.sendMessage(
        "§7Resource: §f" +
        String.format(java.util.Locale.ROOT, "%.2f", state.resource()) +
        "§7, visibility: §f" +
        String.format(java.util.Locale.ROOT, "%.2f", state.visibility()) +
        "§7, nodes: §f" +
        state.remainingNodes() +
        "§7/§f" +
        state.baselineNodes()
      );
    }
  }

  private void force(Player player) {
    boolean ok = plugin
      .regenerationScheduler()
      .forceChunk(ChunkKey.of(player.getLocation()));
    report(player, ok ? 1 : 0);
  }

  private void report(Player player, int regenerated) {
    if (regenerated <= 0) {
      player.sendMessage(Locale.get(player, "settlement.test.force.fail"));
      return;
    }
    player.sendMessage(Locale.get(player, "settlement.test.force.ok", regenerated));
  }

  private void clear(Player player) {
    ChunkKey key = ChunkKey.of(player.getLocation());
    plugin.indicators().remove(key);
    plugin.indicators().save();
    player.sendMessage(Locale.get(player, "settlement.test.clear", key.encode()));
  }

  /** Captures the current chunk's blockstates into the snapshot store. */
  private void capture(Player player, String[] arguments) {
    World world = player.getWorld();
    Chunk chunk = player.getLocation().getChunk();
    String name = arguments.length > 1
      ? arguments[1]
      : "chunk_" + chunk.getX() + "_" + chunk.getZ();
    ChunkSnapshot snapshot = ChunkSnapshot.capture(
      chunk,
      world.getMinHeight(),
      world.getMaxHeight() - 1
    );
    plugin.snapshots().put(name, snapshot);
    player.sendMessage(
      "§aCaptured §f" +
      snapshot.volume() +
      "§a blockstates as §f" +
      name +
      "§a."
    );
  }

  private void simulate(Player player, String[] arguments) {
    String name = arguments.length > 1 ? arguments[1] : null;
    int cycles = 1;
    if (arguments.length > 2) {
      try {
        cycles = Math.max(1, Integer.parseInt(arguments[2]));
      } catch (NumberFormatException ignored) {
        cycles = 1;
      }
    }
    World world = player.getWorld();
    ChunkSnapshot snapshot;
    if (name == null || name.equalsIgnoreCase("synthetic")) {
      ChunkKey chunk = ChunkKey.of(player.getLocation());
      snapshot = ChunkSnapshot.synthetic(
        chunk.x(),
        chunk.z(),
        world.getMinHeight(),
        world.getMaxHeight() - 1,
        plugin.settings().rustMapSeaLevel(),
        world.getSeed()
      );
    } else {
      snapshot = plugin.snapshots().get(name);
      if (snapshot == null) {
        player.sendMessage("§cNo snapshot named " + name + ".");
        return;
      }
    }
    RegenerationSimulator.SimulationReport report = plugin
      .simulator()
      .simulate(snapshot, cycles, world.getSeed());
    if (!report.ok()) {
      player.sendMessage("§cSimulation failed: " + report.notes());
      return;
    }
    player.sendMessage("§8§m------§r §eRegeneration simulation §8§m------");
    player.sendMessage("§7Cycles: §f" + report.cycles());
    player.sendMessage(
      "§7Staging chunk: §f" +
      report.stagingChunkX() +
      "§7, §f" +
      report.stagingChunkZ()
    );
    player.sendMessage(
      "§7Ore blocks: §f" +
      report.oreBlocksBefore() +
      " §7-> §f" +
      report.oreBlocksAfter()
    );
    player.sendMessage(
      "§7Containers: §f" +
      report.containersBefore() +
      " §7-> §f" +
      report.containersAfter()
    );
    player.sendMessage("§7Elapsed: §f" + report.elapsedMillis() + " ms");
    World admin = plugin.adminDimension().world();
    if (admin != null) {
      player.teleport(
        new Location(
          admin,
          (report.stagingChunkX() << 4) + 8.5,
          snapshot.maxY() + 5,
          (report.stagingChunkZ() << 4) + 8.5
        )
      );
      player.sendMessage(
        "§aTeleported to the staged result in the admin dimension."
      );
    }
  }

  private void snapshots(Player player) {
    List<String> names = plugin.snapshots().names();
    player.sendMessage("§eSnapshots (§f" + names.size() + "§e)");
    for (String name : names) {
      player.sendMessage("§7- §f" + name);
    }
  }

  private void admin(Player player) {
    World admin = plugin.adminDimension().ensure();
    if (admin == null) {
      player.sendMessage("§cCould not open the admin dimension.");
      return;
    }
    Location origin = plugin.adminDimension().stagingOrigin();
    player.teleport(origin == null ? admin.getSpawnLocation() : origin);
    player.sendMessage(
      "§aTeleported to the admin dimension §f" + admin.getName() + "§a."
    );
  }

  private void structure(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        "§cUsage: /worldtest structure <minecraft:structure_id>"
      );
      return;
    }
    boolean placed = plugin
      .adminDimension()
      .placeVanillaStructure(arguments[1], player.getLocation());
    player.sendMessage(
      placed
        ? "§aPlaced vanilla structure §f" + arguments[1] + "§a."
        : "§cCould not place " + arguments[1] + " (check the id)."
    );
  }

  private void diag(Player player) {
    int plots = 0;
    for (Settlement settlement : plugin.settlements().all()) {
      plots += plugin.plots().storedPlots(settlement).size();
    }
    ZoneType zone = plugin.settlements().zoneAt(player.getLocation());
    player.sendMessage("§8§m----------§r §eSettlement diagnostics §8§m----------");
    player.sendMessage("§7Zone: §f" + (zone == null ? "unmanaged" : zone));
    player.sendMessage("§7WorldEdit regen: §f" + plugin.regenerator().name());
    player.sendMessage(
      "§7Structure backend: §f" + plugin.structures().bridge().name()
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
      "§7, structure instances: §f" +
      plugin.structures().instances().size()
    );
    player.sendMessage(
      "§7Tracked chunks: §f" + plugin.indicators().all().size()
    );
    player.sendMessage(
      "§7Snapshots: §f" +
      plugin.snapshots().names().size() +
      "§7, simple respawns pending: §f" +
      plugin.simpleResources().tracker().pendingCount()
    );
    player.sendMessage(
      "§7Managed worlds: §f" +
      (plugin.settings().managedWorlds().isEmpty()
        ? "all"
        : String.join(", ", plugin.settings().managedWorlds()))
    );
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    if (arguments.length == 2 && arguments[0].equalsIgnoreCase("simulate")) {
      return plugin.snapshots().names();
    }
    return SUBCOMMANDS;
  }
}
