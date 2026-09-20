package dev.bisz.worldgen.commands;

import dev.bisz.worldgen.WorldGenPlugin;
import dev.bisz.worldgen.model.ChunkKey;
import dev.bisz.worldgen.structure.StructureDefinition;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Administrative world generation and POI management. */
public final class WorldAdminCommand extends DevCommand {

  private static final List<String> SUBCOMMANDS = List.of(
    "reload",
    "diag",
    "pregen",
    "cycle",
    "regenerate",
    "clear",
    "hud",
    "poi"
  );

  private final WorldGenPlugin plugin;

  public WorldAdminCommand(WorldGenPlugin plugin) {
    super("worldadmin", "worldgen.admin");
    this.plugin = plugin;
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] arguments
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Locale.get("en_us", "worldgen.command.player_only"));
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
      case "poi" -> poi(player, arguments);
      default -> player.sendMessage(
        Locale.get(player, "worldgen.command.unknown", arguments[0])
      );
    }
    return true;
  }

  private void help(Player player) {
    player.sendMessage("§8§m------§r §e/worldadmin §8§m------");
    player.sendMessage("§freload §7- reload chunk indicators and POI definitions");
    player.sendMessage("§fdiag §7- backends, counts, and status");
    player.sendMessage("§fpregen [radius] §7- generate the whole island (default)");
    player.sendMessage("§fcycle [seconds|off] §7- override the regen cycle (testing)");
    player.sendMessage("§fregenerate [radius] §7- regenerate the current chunk or an area");
    player.sendMessage("§fclear §7- clear the current chunk's indicators");
    player.sendMessage("§fhud §7- toggle the indicator HUD");
    player.sendMessage("§fpoi <list|reload> §7- stored POI schematics");
  }

  private void reload(Player player) {
    plugin.reloadData();
    plugin.pois().reload();
    player.sendMessage(Locale.get(player, "worldgen.reload"));
  }

  private void diag(Player player) {
    player.sendMessage("§8§m----------§r §eWorld diagnostics §8§m----------");
    player.sendMessage("§7Managed world: §f" + plugin.setup().managedWorldName());
    player.sendMessage("§7WorldEdit regen: §f" + plugin.regenerator().name());
    player.sendMessage(
      "§7Structure backend: §f" + plugin.pois().bridge().name()
    );
    player.sendMessage(
      "§7POI definitions: §f" +
      plugin.pois().definitions().size() +
      "§7, live POIs: §f" +
      plugin.pois().liveCount()
    );
    player.sendMessage(
      "§7Auto-regen: §f" + plugin.regenerationScheduler().status()
    );
    String here = plugin
      .regenerationScheduler()
      .chunkStatus(ChunkKey.of(player.getLocation()));
    player.sendMessage(
      "§7This chunk: §f" + (here == null ? "untracked" : here)
    );
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

  private void poi(Player player, String[] arguments) {
    if (arguments.length < 2) {
      player.sendMessage(
        Locale.get(player, "worldgen.command.usage", "/worldadmin poi <list|reload>")
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
        Locale.get(player, "worldgen.command.unknown", arguments[1])
      );
    }
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
      if (arguments[0].equalsIgnoreCase("poi")) {
        return List.of("list", "reload");
      }
      if (arguments[0].equalsIgnoreCase("regenerate")) {
        return List.of("stop");
      }
    }
    return List.of();
  }
}
