package dev.bisz.world.commands;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.ChunkKey;
import dev.bisz.commands.DevCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/**
 * Console-friendly diagnostics used by the devmc testing MCP: chunk probes,
 * block reads, seed info, per-material counts, and forced regeneration.
 */
public final class WorldInfoCommand extends DevCommand {

  private final WorldPlugin plugin;

  public WorldInfoCommand(WorldPlugin plugin) {
    super("worldinfo", "world.admin");
    this.plugin = plugin;
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] arguments
  ) {
    if (arguments.length == 0) {
      sender.sendMessage(
        "Usage: worldinfo <probe|block|seed|gen|regen|biome|deplete> [world] <coords>"
      );
      return true;
    }
    switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
      case "probe" -> probe(sender, arguments);
      case "block" -> block(sender, arguments);
      case "seed" -> seed(sender);
      case "gen" -> gen(sender);
      case "regen" -> regen(sender, arguments);
      case "maketest" -> makeTest(sender, arguments);
      case "setblock" -> setBlock(sender, arguments);
      case "folder" -> folder(sender, arguments);
      case "orecount" -> oreCount(sender, arguments);
      case "biome" -> biome(sender, arguments);
      case "biomes" -> biomes(sender);
      case "biomescan" -> biomeScan(sender, arguments);
      case "biomecheck" -> biomeCheck(sender, arguments);
      case "caves" -> caves(sender, arguments);
      case "regenstatus" -> regenStatus(sender);
      case "cycle" -> cycle(sender, arguments);
      case "pregen" -> pregen(sender, arguments);
      case "pregenstatus" -> pregenStatus(sender);
      case "regennext" -> regenNext(sender, arguments);
      case "chunk" -> chunk(sender, arguments);
      case "extract" -> extract(sender, arguments);
      case "spawn" -> spawn(sender);
      case "deplete" -> deplete(sender, arguments);
      case "explode" -> explode(sender, arguments);
      case "regencheck" -> regenCheck(sender, arguments);
      default -> sender.sendMessage("Unknown worldinfo subcommand.");
    }
    return true;
  }

  /** Resolves an optional leading world name, defaulting to the managed world. */
  private World pickWorld(String[] arguments) {
    if (arguments.length >= 2) {
      World named = Bukkit.getWorld(arguments[1]);
      if (named != null) return named;
    }
    return plugin.setup().ensureWorld();
  }

  /** One when the first coordinate argument is actually a world name. */
  private int worldOffset(String[] arguments) {
    if (arguments.length >= 2 && Bukkit.getWorld(arguments[1]) != null) {
      return 1;
    }
    return 0;
  }

  private void seed(CommandSender sender) {
    World world = plugin.setup().ensureWorld();
    if (world == null) {
      sender.sendMessage("worldinfo: no managed world");
      return;
    }
    sender.sendMessage(
      "world=" +
      world.getName() +
      " seed=" +
      world.getSeed() +
      " generator=" +
      world.getGenerator().getClass().getSimpleName()
    );
  }

  /** Per-material counts for the chunk containing the world spawn. */
  private void gen(CommandSender sender) {
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    int chunkX = world.getSpawnLocation().getBlockX() >> 4;
    int chunkZ = world.getSpawnLocation().getBlockZ() >> 4;
    int water = 0;
    int stone = 0;
    int deepslate = 0;
    int air = 0;
    int sand = 0;
    int grass = 0;
    int dirt = 0;
    int other = 0;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
          Material material = world
            .getBlockAt((chunkX << 4) + x, y, (chunkZ << 4) + z)
            .getType();
          if (material == Material.WATER) {
            water++;
          } else if (material == Material.STONE) {
            stone++;
          } else if (material == Material.DEEPSLATE) {
            deepslate++;
          } else if (material.isAir()) {
            air++;
          } else if (material == Material.SAND || material == Material.GRAVEL) {
            sand++;
          } else if (material == Material.GRASS_BLOCK) {
            grass++;
          } else if (material == Material.DIRT) {
            dirt++;
          } else {
            other++;
          }
        }
      }
    }
    sender.sendMessage(
      String.format(
        "chunk %d,%d: water=%d stone=%d deepslate=%d air=%d sand/gravel=%d grass=%d dirt=%d other=%d",
        chunkX,
        chunkZ,
        water,
        stone,
        deepslate,
        air,
        sand,
        grass,
        dirt,
        other
      )
    );
  }

  /** Column samples for a chunk. Usage: worldinfo probe [world] <chunkX> <chunkZ> */
  private void probe(CommandSender sender, String[] arguments) {
    World world = pickWorld(arguments);
    if (world == null) {
      sender.sendMessage("worldinfo: no such world");
      return;
    }
    int base = 1 + worldOffset(arguments);
    if (arguments.length < base + 2) {
      sender.sendMessage("Usage: worldinfo probe [world] <chunkX> <chunkZ>");
      return;
    }
    int chunkX = parseInt(arguments[base], 0);
    int chunkZ = parseInt(arguments[base + 1], 0);
    StringBuilder out = new StringBuilder();
    out
      .append("probe chunk ")
      .append(chunkX)
      .append(',')
      .append(chunkZ)
      .append(" world=")
      .append(world.getName());
    for (int[] cell : new int[][] { { 0, 0 }, { 8, 8 }, { 15, 15 } }) {
      int x = (chunkX << 4) + cell[0];
      int z = (chunkZ << 4) + cell[1];
      int surface = world.getHighestBlockYAt(x, z);
      StringBuilder column = new StringBuilder();
      column
        .append("col(")
        .append(x)
        .append(',')
        .append(z)
        .append(") surface=")
        .append(surface)
        .append(" ");
      for (
        int y = surface;
        y >= Math.max(world.getMinHeight(), surface - 14);
        y--
      ) {
        column
          .append(y)
          .append(':')
          .append(world.getBlockAt(x, y, z).getType().name())
          .append(' ');
      }
      out.append('\n').append(column);
    }
    sender.sendMessage(out.toString());
  }

  /** Usage: worldinfo block [world] <x> <y> <z> */
  private void block(CommandSender sender, String[] arguments) {
    World world = pickWorld(arguments);
    if (world == null) return;
    int base = 1 + worldOffset(arguments);
    if (arguments.length < base + 3) {
      sender.sendMessage("Usage: worldinfo block [world] <x> <y> <z>");
      return;
    }
    int x = parseInt(arguments[base], 0);
    int y = parseInt(arguments[base + 1], 64);
    int z = parseInt(arguments[base + 2], 0);
    sender.sendMessage(
      "block " +
      x +
      ',' +
      y +
      ',' +
      z +
      " = " +
      world.getBlockAt(x, y, z).getType().name()
    );
  }

  /** Usage: worldinfo regen <chunkX> <chunkZ> */
  private void regen(CommandSender sender, String[] arguments) {
    if (arguments.length < 3) {
      sender.sendMessage("Usage: worldinfo regen <chunkX> <chunkZ>");
      return;
    }
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    ChunkKey key = new ChunkKey(
      world.getName(),
      parseInt(arguments[1], 0),
      parseInt(arguments[2], 0)
    );
    long started = System.nanoTime();
    boolean ok = plugin.regenerationScheduler().forceChunk(key);
    long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
    sender.sendMessage(
      "regen " + key.encode() + " = " + ok + " in " + elapsedMillis + "ms"
    );
  }

  /**
   * Creates a test world for comparing terrain.
   * Usage: worldinfo maketest <name> <seed> [bare]
   */
  private void makeTest(CommandSender sender, String[] arguments) {
    if (arguments.length < 3) {
      sender.sendMessage("Usage: worldinfo maketest <name> <seed> [bare]");
      return;
    }
    String name = arguments[1];
    if (Bukkit.getWorld(name) != null) {
      sender.sendMessage(name + " already exists");
      return;
    }
    long seed;
    try {
      seed = Long.parseLong(arguments[2]);
    } catch (NumberFormatException exception) {
      sender.sendMessage("bad seed: " + arguments[2]);
      return;
    }
    org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(name);
    creator.seed(seed);
    creator.generateStructures(false);
    if (arguments.length > 3) {
      if (arguments[3].equalsIgnoreCase("tweak")) {
        creator.generator(
          new dev.bisz.world.worldgen.BareboneGenerator(
            plugin.settings().terrainAmplitude(),
            plugin.settings().terrainCaves(),
            plugin.settings().terrainCaveScale(),
            plugin.settings().terrainCaveThreshold(),
            63
          )
        );
      } else if (arguments[3].equalsIgnoreCase("island")) {
        creator.generator(
          dev.bisz.world.worldgen.WorldGenerators.create(plugin.settings())
        );
      } else {
        creator.generator(
          new dev.bisz.world.worldgen.BareboneGenerator(1.0, false, 0.06, 0.62, 63)
        );
      }
    }
    World created = creator.createWorld();
    sender.sendMessage(
      "created " + (created == null ? "null" : created.getName()) + " seed=" + seed
    );
  }

  /** Usage: worldinfo setblock <x> <y> <z> <material> */
  private void setBlock(CommandSender sender, String[] arguments) {
    if (arguments.length < 5) {
      sender.sendMessage("Usage: worldinfo setblock <x> <y> <z> <material>");
      return;
    }
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    int x = parseInt(arguments[1], 0);
    int y = parseInt(arguments[2], 64);
    int z = parseInt(arguments[3], 0);
    Material material = Material.matchMaterial(arguments[4]);
    if (material == null) {
      sender.sendMessage("unknown material " + arguments[4]);
      return;
    }
    world.getBlockAt(x, y, z).setType(material, false);
    sender.sendMessage(
      "set " + x + ',' + y + ',' + z + " = " + world.getBlockAt(x, y, z).getType()
    );
  }

  /** Usage: worldinfo folder <chunkX> <chunkZ> */
  private void folder(CommandSender sender, String[] arguments) {
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    int chunkX = arguments.length > 1 ? parseInt(arguments[1], 0) : 0;
    int chunkZ = arguments.length > 2 ? parseInt(arguments[2], 0) : 0;
    java.io.File region = new java.io.File(
      world.getWorldFolder(),
      "region/r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca"
    );
    sender.sendMessage(
      "folder=" +
      world.getWorldFolder() +
      " region=" +
      region +
      " exists=" +
      region.isFile()
    );
  }

  /** Usage: worldinfo orecount <world> <chunkX> <chunkZ> */
  private void oreCount(CommandSender sender, String[] arguments) {
    if (arguments.length < 4) {
      sender.sendMessage("Usage: worldinfo orecount <world> <chunkX> <chunkZ>");
      return;
    }
    World world = Bukkit.getWorld(arguments[1]);
    if (world == null) {
      sender.sendMessage("no such world " + arguments[1]);
      return;
    }
    int chunkX = parseInt(arguments[2], 0);
    int chunkZ = parseInt(arguments[3], 0);
    int ores = 0;
    java.util.Map<String, Integer> byType = new java.util.TreeMap<>();
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
          Material material = world
            .getBlockAt((chunkX << 4) + x, y, (chunkZ << 4) + z)
            .getType();
          if (material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS) {
            ores++;
            byType.merge(material.name(), 1, Integer::sum);
          }
        }
      }
    }
    StringBuilder breakdown = new StringBuilder();
    for (java.util.Map.Entry<String, Integer> entry : byType.entrySet()) {
      breakdown.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
    }
    sender.sendMessage(
      "orecount " +
      world.getName() +
      " " +
      chunkX +
      "," +
      chunkZ +
      ": " +
      ores +
      breakdown
    );
  }

  /**
   * Forces a chunk to look fully depleted so the automatic scheduler can be
   * observed picking it up. Usage: worldinfo deplete <chunkX> <chunkZ>
   */
  private void deplete(CommandSender sender, String[] arguments) {
    if (arguments.length < 3) {
      sender.sendMessage("Usage: worldinfo deplete <chunkX> <chunkZ>");
      return;
    }
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    ChunkKey key = new ChunkKey(
      world.getName(),
      parseInt(arguments[1], 0),
      parseInt(arguments[2], 0)
    );
    long now = System.currentTimeMillis();
    var state = plugin.indicators().getOrCreate(key, now);
    if (state.baselineNodes() <= 0) {
      state.ensureBaseline(
        Math.max(1, plugin.features().estimateBaseline(world))
      );
    }
    // Mark as fully extracted and due immediately, so the next evaluation
    // schedules and resets it (subject to the idle/pin gates).
    state.depleteNodes(plugin.settings().regenDepletionNodes());
    state.clearActivity();
    state.schedule(now);
    sender.sendMessage(
      "depleted " +
      key.encode() +
      " resource=" +
      state.resource() +
      " dueNow=" +
      state.isDue(now)
    );
  }

  /** Lists the biomes the island generator can place, without generating. */
  private void biomes(CommandSender sender) {
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    if (
      world.getGenerator() instanceof
      dev.bisz.world.worldgen.IslandWorldGenerator island
    ) {
      sender.sendMessage(
        "island biomes=" +
        island
          .provider()
          .configuredBiomes()
          .stream()
          .map(biome -> biome.getKey().getKey())
          .toList()
      );
    } else {
      sender.sendMessage(
        "generator=" + world.getGenerator().getClass().getSimpleName()
      );
    }
  }

  /**
   * Counts the island layout's biomes without generating anything, so coverage
   * can be checked cheaply. Usage: worldinfo biomecheck [step]
   */
  private void biomeCheck(CommandSender sender, String[] arguments) {
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    if (
      !(world.getGenerator() instanceof
        dev.bisz.world.worldgen.IslandWorldGenerator island)
    ) {
      sender.sendMessage(
        "generator=" + world.getGenerator().getClass().getSimpleName()
      );
      return;
    }
    int step = Math.max(
      16,
      arguments.length > 1 ? parseInt(arguments[1], 64) : 64
    );
    int radius = island.mask().islandRadius();
    double[] erosions = { 0.0, -0.4, -1.0 };
    java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
    for (int x = -radius; x <= radius; x += step) {
      for (int z = -radius; z <= radius; z += step) {
        for (double erosion : erosions) {
          counts.merge(
            island
              .provider()
              .biomeAt(world.getSeed(), x, z, 0.2, 0.0, 0.0, 0.0, erosion)
              .getKey()
              .getKey(),
            1,
            Integer::sum
          );
        }
      }
    }
    sender.sendMessage("biomecheck step=" + step + " " + counts);
  }

  /** Counts air below y=60 in a chunk, as a cave check. */
  private void caves(CommandSender sender, String[] arguments) {
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    int chunkX = arguments.length > 1 ? parseInt(arguments[1], 0) : 0;
    int chunkZ = arguments.length > 2 ? parseInt(arguments[2], 0) : 0;
    world.getChunkAt(chunkX, chunkZ);
    int air = 0;
    int total = 0;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = world.getMinHeight() + 5; y <= 60; y++) {
          total++;
          if (
            world
              .getBlockAt((chunkX << 4) + x, y, (chunkZ << 4) + z)
              .getType()
              .isAir()
          ) {
            air++;
          }
        }
      }
    }
    sender.sendMessage(
      "caves chunk " + chunkX + "," + chunkZ + " airBelow60=" + air + "/" + total
    );
  }

  /**
   * Generates a square of chunks around spawn. Defaults to the whole island.
   * Usage: worldinfo pregen [radius|stop]
   */
  private void pregen(CommandSender sender, String[] arguments) {
    World world = plugin.setup().ensureWorld();
    if (world == null) {
      sender.sendMessage("no managed world");
      return;
    }
    if (arguments.length > 1 && arguments[1].equalsIgnoreCase("stop")) {
      plugin.areaRegenerator().stop();
      sender.sendMessage("pregen stopped");
      return;
    }
    int defaultRadius = Math.max(1, plugin.settings().islandSize() / 2 / 16);
    int radius = arguments.length > 1
      ? parseInt(arguments[1], defaultRadius)
      : defaultRadius;
    if (radius <= 0) radius = defaultRadius;
    plugin
      .areaRegenerator()
      .startGenerate(world, world.getSpawnLocation(), radius, null);
    sender.sendMessage(
      "pregen radius=" +
      radius +
      " total=" +
      (2 * radius + 1) * (2 * radius + 1) +
      " chunks"
    );
  }

  /** Reports area generation progress. */
  private void pregenStatus(CommandSender sender) {
    var area = plugin.areaRegenerator();
    sender.sendMessage(
      "pregen running=" +
      area.running() +
      " done=" +
      area.done() +
      "/" +
      area.total()
    );
  }

  /** Prints the automatic regeneration status. */
  private void regenStatus(CommandSender sender) {
    sender.sendMessage(
      "auto-regen: " + plugin.regenerationScheduler().status()
    );
  }

  /**
   * Overrides the reset cycle at runtime for testing, and pulls already
   * scheduled chunks in to the new cycle.
   * Usage: worldinfo cycle &lt;seconds|off&gt;
   */
  private void cycle(CommandSender sender, String[] arguments) {
    if (arguments.length < 2 || arguments[1].equalsIgnoreCase("off")) {
      int rescheduled = plugin.regenerationScheduler().setCycleOverride(0L);
      sender.sendMessage(
        "regen cycle override cleared (" +
        plugin.settings().regenCycleMillis() / 1000L +
        "s); rescheduled " +
        rescheduled +
        " chunk(s)"
      );
      return;
    }
    int seconds = parseInt(arguments[1], -1);
    if (seconds <= 0) {
      int rescheduled = plugin.regenerationScheduler().setCycleOverride(0L);
      sender.sendMessage(
        "regen cycle override cleared; rescheduled " + rescheduled + " chunk(s)"
      );
      return;
    }
    int rescheduled = plugin.regenerationScheduler().setCycleOverride(
      seconds * 1000L
    );
    sender.sendMessage(
      "regen cycle = " +
      seconds +
      "s (test); rescheduled " +
      rescheduled +
      " chunk(s)"
    );
  }

  /**
   * Reports one chunk's zone and regeneration state.
   * Usage: worldinfo chunk [chunkX] [chunkZ]
   */
  private void chunk(CommandSender sender, String[] arguments) {
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    int chunkX = arguments.length > 1
      ? parseInt(arguments[1], 0)
      : world.getSpawnLocation().getBlockX() >> 4;
    int chunkZ = arguments.length > 2
      ? parseInt(arguments[2], 0)
      : world.getSpawnLocation().getBlockZ() >> 4;
    ChunkKey key = new ChunkKey(world.getName(), chunkX, chunkZ);
    var zone = plugin
      .settlements()
      .zoneAt(
        new org.bukkit.Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8)
      );
    String status = plugin.regenerationScheduler().chunkStatus(key);
    sender.sendMessage(
      "chunk " +
      key.encode() +
      " zone=" +
      zone +
      " " +
      (status == null ? "untracked" : status)
    );
  }

  /** Lists the next scheduled resets. Usage: worldinfo regennext [limit] */
  private void regenNext(CommandSender sender, String[] arguments) {
    int limit = arguments.length > 1 ? parseInt(arguments[1], 5) : 5;
    for (String line : plugin.regenerationScheduler().nextDue(limit)) {
      sender.sendMessage(line);
    }
  }

  /**
   * Simulates mining in a chunk so the scheduling path (not the immediate
   * reset) can be observed. Usage: worldinfo extract <chunkX> <chunkZ> [nodes]
   */
  private void extract(CommandSender sender, String[] arguments) {
    if (arguments.length < 3) {
      sender.sendMessage("Usage: worldinfo extract <chunkX> <chunkZ> [nodes]");
      return;
    }
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    ChunkKey key = new ChunkKey(
      world.getName(),
      parseInt(arguments[1], 0),
      parseInt(arguments[2], 0)
    );
    int nodes = arguments.length > 3 ? parseInt(arguments[3], 1) : 1;
    long now = System.currentTimeMillis();
    var state = plugin.indicators().getOrCreate(key, now);
    if (state.baselineNodes() <= 0) {
      state.ensureBaseline(
        Math.max(1, plugin.features().estimateBaseline(world))
      );
    }
    state.clearActivity();
    state.depleteNodes(nodes);
    sender.sendMessage(
      "extracted " +
      nodes +
      " from " +
      key.encode() +
      " total=" +
      state.extractedNodes() +
      " (threshold " +
      plugin.settings().regenDepletionNodes() +
      ")"
    );
  }

  /** Reports the world spawn and whether it sits on land. */
  private void spawn(CommandSender sender) {
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    var location = world.getSpawnLocation();
    int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
    sender.sendMessage(
      "spawn=" +
      location.getBlockX() +
      "," +
      location.getBlockY() +
      "," +
      location.getBlockZ() +
      " surface=" +
      y +
      " block=" +
      world.getBlockAt(location.getBlockX(), y, location.getBlockZ()).getType().name()
    );
  }

  /**
   * Counts biomes over already-loaded chunks only, so it never triggers heavy
   * generation. Usage: worldinfo biomescan [step]
   */
  private void biomeScan(CommandSender sender, String[] arguments) {
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    int step = Math.max(
      16,
      arguments.length > 1 ? parseInt(arguments[1], 64) : 64
    );
    int radius = (int) (world.getWorldBorder().getSize() / 2.0);
    if (radius <= 0) radius = plugin.settings().islandSize() / 2;
    java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
    int scanned = 0;
    for (int x = -radius; x <= radius; x += step) {
      for (int z = -radius; z <= radius; z += step) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
        int y = world.getHighestBlockYAt(x, z);
        counts.merge(world.getBiome(x, y, z).getKey().getKey(), 1, Integer::sum);
        scanned++;
      }
    }
    sender.sendMessage(
      "biomescan step=" + step + " scanned=" + scanned + " " + counts
    );
  }

  /** Usage: worldinfo biome [world] <x> <z> */
  private void biome(CommandSender sender, String[] arguments) {
    World world = pickWorld(arguments);
    if (world == null) return;
    int base = 1 + worldOffset(arguments);
    if (arguments.length < base + 2) {
      sender.sendMessage("Usage: worldinfo biome [world] <x> <z>");
      return;
    }
    int x = parseInt(arguments[base], 0);
    int z = parseInt(arguments[base + 1], 0);
    int y = world.getHighestBlockYAt(x, z);
    sender.sendMessage(
      "biome " + x + ',' + z + " = " + world.getBiome(x, y, z).getKey()
    );
  }

  /**
   * Spawns a non-player explosion in a chunk, to exercise change detection and
   * the dirty-regeneration path. Usage:
   * {@code worldinfo explode <chunkX> <chunkZ> [power] [y]}
   */
  private void explode(CommandSender sender, String[] arguments) {
    if (arguments.length < 3) {
      sender.sendMessage(
        "Usage: worldinfo explode <chunkX> <chunkZ> [power] [y]"
      );
      return;
    }
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    int chunkX = parseInt(arguments[1], 0);
    int chunkZ = parseInt(arguments[2], 0);
    double power = parseDouble(
      arguments.length > 3 ? arguments[3] : null,
      4.0
    );
    int x = (chunkX << 4) + 8;
    int z = (chunkZ << 4) + 8;
    int y = arguments.length > 4
      ? parseInt(arguments[4], 64)
      : world.getHighestBlockYAt(x, z) + 1;
    world.getChunkAt(chunkX, chunkZ);
    boolean broke = world.createExplosion(
      x + 0.5,
      y,
      z + 0.5,
      (float) power,
      false,
      true
    );
    sender.sendMessage(
      "explode " +
      chunkX +
      "," +
      chunkZ +
      " at " +
      x +
      "," +
      y +
      "," +
      z +
      " power=" +
      power +
      " broke=" +
      broke
    );
  }

  /**
   * One-shot regeneration diagnostic for a chunk: zone, state, ore count, and
   * non-air block count. Usage: {@code worldinfo regencheck <chunkX> <chunkZ>}
   */
  private void regenCheck(CommandSender sender, String[] arguments) {
    if (arguments.length < 3) {
      sender.sendMessage("Usage: worldinfo regencheck <chunkX> <chunkZ>");
      return;
    }
    World world = plugin.setup().ensureWorld();
    if (world == null) return;
    int chunkX = parseInt(arguments[1], 0);
    int chunkZ = parseInt(arguments[2], 0);
    world.getChunkAt(chunkX, chunkZ);
    int ores = 0;
    int nonAir = 0;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
          Material material = world
            .getBlockAt((chunkX << 4) + x, y, (chunkZ << 4) + z)
            .getType();
          if (material.isAir()) continue;
          nonAir++;
          if (
            material.name().endsWith("_ORE") ||
            material == Material.ANCIENT_DEBRIS
          ) {
            ores++;
          }
        }
      }
    }
    ChunkKey key = new ChunkKey(world.getName(), chunkX, chunkZ);
    var zone = plugin
      .settlements()
      .zoneAt(
        new org.bukkit.Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8)
      );
    String status = plugin.regenerationScheduler().chunkStatus(key);
    sender.sendMessage(
      "regencheck " +
      key.encode() +
      " zone=" +
      zone +
      " ores=" +
      ores +
      " nonAir=" +
      nonAir +
      " " +
      (status == null ? "untracked" : status)
    );
  }

  private static int parseInt(String value, int fallback) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private static double parseDouble(String value, double fallback) {
    if (value == null) return fallback;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }
}
