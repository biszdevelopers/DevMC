package dev.bisz.world.setup;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.worldgen.WorldGenerators;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * Idempotent world initialization. Runs automatically when the server starts:
 * it creates the managed world with the configured generator, applies gamerules
 * and difficulty, sets the spawn and world border, founds the spawn settlement,
 * and starts generating chunks. No administrator action is required.
 */
public final class SetupService {

  /** One completed setup step. */
  public record Step(String name, boolean ok, String detail) {}

  /** The ordered result of a setup run. */
  public record Report(List<Step> steps) {
    /** Whether every step succeeded. */
    public boolean success() {
      for (Step step : steps) {
        if (!step.ok()) return false;
      }
      return true;
    }
  }

  private final WorldPlugin plugin;

  public SetupService(WorldPlugin plugin) {
    this.plugin = plugin;
  }

  /** The name of the world this plugin creates and manages. */
  public String managedWorldName() {
    String name = plugin.settings().setupWorldName();
    return name == null || name.isBlank() ? "devmc" : name;
  }

  /** Runs the full setup using configured defaults. */
  public Report run() {
    return run(null);
  }

  /**
   * Runs setup. The reference location, when supplied and inside the managed
   * world, becomes the world spawn; otherwise the existing spawn is kept.
   */
  public Report run(Location reference) {
    List<Step> steps = new ArrayList<>();
    World world = resolveWorld(steps);
    if (world == null) return new Report(List.copyOf(steps));
    applyGamerules(world, steps);
    applyDifficulty(world, steps);
    Location spawn = applySpawn(world, reference, steps);
    applyBorder(world, spawn, steps);
    applySettlement(spawn, steps);
    applyGenerate(world, steps);
    return new Report(List.copyOf(steps));
  }

  /** Creates or adopts the managed world without touching anything else. */
  public World ensureWorld() {
    return resolveWorld(new ArrayList<>());
  }

  /**
   * Re-applies the configured gamerules and difficulty to an existing world.
   * Called on every startup so a settings change (for example
   * {@code setup.gamerule.mobGriefing}) takes effect without recreating the
   * world.
   */
  public void applyWorldRules(World world) {
    if (world == null) return;
    List<Step> steps = new ArrayList<>();
    applyGamerules(world, steps);
    applyDifficulty(world, steps);
    for (Step step : steps) {
      plugin
        .getLogger()
        .info("world rules " + step.name() + ": " + step.detail());
    }
  }

  private World resolveWorld(List<Step> steps) {
    String name = managedWorldName();
    World existing = Bukkit.getWorld(name);
    if (existing != null) {
      steps.add(new Step("world", true, "Found " + name));
      return existing;
    }

    String configured = plugin.settings().setupSeed();
    boolean fixedSeed = configured != null && !configured.isBlank();
    long seed = fixedSeed ? parseSeed(configured) : new Random().nextLong();
    // With a random seed the island core can land in an ocean; try a few seeds
    // until the centre is solid land so the settlement has somewhere to go.
    int attempts = fixedSeed ? 1 : MAX_SEED_ATTEMPTS;
    World last = null;
    for (int attempt = 1; attempt <= attempts; attempt++) {
      World created = createWorld(name, seed);
      if (created == null) {
        steps.add(new Step("world", false, "Could not create " + name));
        return null;
      }
      last = created;
      if (attempt == attempts || isLandWorld(created)) {
        steps.add(
          new Step(
            "world",
            true,
            "Created " + name + " seed=" + seed + " (attempt " + attempt + ")"
          )
        );
        return created;
      }
      java.nio.file.Path folder = created.getWorldFolder().toPath();
      Bukkit.unloadWorld(created, false);
      deleteRecursively(folder);
      seed = new Random().nextLong();
    }
    steps.add(new Step("world", true, "Created " + name));
    return last;
  }

  private World createWorld(String name, long seed) {
    return new WorldCreator(name)
      .seed(seed)
      .environment(parseEnvironment(plugin.settings().setupEnvironment()))
      .type(parseWorldType(plugin.settings().setupWorldType()))
      .generateStructures(plugin.settings().setupGenerateStructures())
      .generator(WorldGenerators.create(plugin.settings()))
      .createWorld();
  }

  /**
   * Whether the centre is land, most of a ring around it is land, and there is
   * some high ground so a mountain biome (and therefore emerald) can appear.
   */
  private boolean isLandWorld(World world) {
    int seaLevel = plugin.settings().islandSeaLevel();
    if (world.getHighestBlockYAt(0, 0) <= seaLevel) return false;
    int[][] samples = {
      { 96, 0 },
      { -96, 0 },
      { 0, 96 },
      { 0, -96 },
      { 192, 0 },
      { -192, 0 },
      { 0, 192 },
      { 0, -192 },
      { 288, 0 },
      { -288, 0 },
      { 0, 288 },
      { 0, -288 },
    };
    int land = 0;
    int high = 0;
    for (int[] sample : samples) {
      int y = world.getHighestBlockYAt(sample[0], sample[1]);
      if (y > seaLevel) land++;
      if (y > seaLevel + MOUNTAIN_HEIGHT) high++;
    }
    return land >= (int) Math.ceil(samples.length * 0.65) && high >= 1;
  }

  private static long parseSeed(String value) {
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException exception) {
      return value.hashCode();
    }
  }

  private static void deleteRecursively(java.nio.file.Path path) {
    if (path == null || !java.nio.file.Files.exists(path)) return;
    try (
      java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(
        path
      )
    ) {
      walk
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(candidate -> {
          try {
            java.nio.file.Files.deleteIfExists(candidate);
          } catch (java.io.IOException ignored) {
            // Best effort; a leftover file just means the next seed is skipped.
          }
        });
    } catch (java.io.IOException ignored) {
      // Best effort.
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void applyGamerules(World world, List<Step> steps) {
    Map<String, GameRule<?>> index = gameRuleIndex();
    int applied = 0;
    int skipped = 0;
    for (Map.Entry<String, String> entry : plugin
      .settings()
      .setupGamerules()
      .entrySet()) {
      GameRule rule = index.get(normalizeRule(entry.getKey()));
      if (rule == null) {
        skipped++;
        continue;
      }
      try {
        if (rule.getType() == Boolean.class) {
          world.setGameRule(
            (GameRule<Boolean>) rule,
            Boolean.parseBoolean(entry.getValue())
          );
        } else if (rule.getType() == Integer.class) {
          world.setGameRule(
            (GameRule<Integer>) rule,
            Integer.parseInt(entry.getValue().trim())
          );
        } else {
          skipped++;
          continue;
        }
        applied++;
      } catch (RuntimeException exception) {
        skipped++;
      }
    }
    steps.add(
      new Step(
        "gamerules",
        skipped == 0,
        applied + " applied, " + skipped + " skipped"
      )
    );
  }

  private void applyDifficulty(World world, List<Step> steps) {
    String configured = plugin.settings().setupDifficulty();
    try {
      Difficulty difficulty = Difficulty.valueOf(
        configured.toUpperCase(Locale.ROOT)
      );
      world.setDifficulty(difficulty);
      steps.add(new Step("difficulty", true, difficulty.name()));
    } catch (IllegalArgumentException exception) {
      steps.add(
        new Step("difficulty", false, "Unknown difficulty " + configured)
      );
    }
  }

  private Location applySpawn(
    World world,
    Location reference,
    List<Step> steps
  ) {
    if (
      reference == null ||
      reference.getWorld() == null ||
      !reference.getWorld().equals(world)
    ) {
      steps.add(new Step("spawn", true, "Kept existing spawn"));
      return world.getSpawnLocation();
    }
    Location spawn = new Location(
      world,
      reference.getBlockX() + 0.5,
      reference.getBlockY(),
      reference.getBlockZ() + 0.5,
      reference.getYaw(),
      reference.getPitch()
    );
    world.setSpawnLocation(spawn);
    steps.add(
      new Step(
        "spawn",
        true,
        spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ()
      )
    );
    return spawn;
  }

  private void applyBorder(
    World world,
    Location spawn,
    List<Step> steps
  ) {
    // The Rust map is a bounded island: keep the border on its edge so players
    // cannot wander into unbounded ocean.
    double size = plugin.settings().islandEnabled()
      ? plugin.settings().islandSize()
      : plugin.settings().setupBorderSize();
    if (size <= 0.0) {
      steps.add(new Step("border", true, "Unchanged"));
      return;
    }
    WorldBorder border = world.getWorldBorder();
    border.setCenter(spawn.getX(), spawn.getZ());
    border.setSize(size);
    steps.add(new Step("border", true, "size " + (long) size));
  }

  private void applySettlement(Location spawn, List<Step> steps) {
    if (!plugin.settings().setupSpawnSettlement()) {
      steps.add(new Step("settlement", true, "Skipped"));
      return;
    }
    ChunkKey key = ChunkKey.of(spawn);
    if (plugin.settlements().isSettlement(key)) {
      steps.add(new Step("settlement", true, "Spawn already in a settlement"));
      return;
    }
    Settlement settlement = plugin
      .settlements()
      .create(plugin.settings().setupSettlementName(), spawn);
    steps.add(new Step("settlement", true, "Created " + settlement.name()));
  }

  private void applyGenerate(World world, List<Step> steps) {
    int radius = plugin.settings().pregenRadiusChunks();
    if (radius <= 0) {
      radius = defaultPregenRadius();
    }
    if (radius <= 0) {
      steps.add(new Step("generate", true, "Skipped"));
      return;
    }
    plugin
      .areaRegenerator()
      .startGenerate(world, world.getSpawnLocation(), radius, null);
    steps.add(new Step("generate", true, "Started radius " + radius));
  }

  /**
   * Cap on the derived pregen radius. A custom-generator chunk costs tens of
   * milliseconds, so pregenerating a full 2k map synchronously would stall
   * startup; the rest of the map generates on demand. Administrators can raise
   * {@code pregen.radius_chunks} to pregenerate more.
   */
  private static final int MAX_DEFAULT_PREGEN_RADIUS = 12;

  /** How many random seeds to try before accepting a poor island. */
  private static final int MAX_SEED_ATTEMPTS = 24;

  /** Surface height (above sea level) that counts as a mountain. */
  private static final int MOUNTAIN_HEIGHT = 45;

  private int defaultPregenRadius() {
    if (plugin.settings().islandEnabled()) {
      int radius = plugin.settings().islandSize() / 2 / 16 + 1;
      return Math.max(1, Math.min(radius, MAX_DEFAULT_PREGEN_RADIUS));
    }
    double border = plugin.settings().setupBorderSize();
    if (border > 0.0) {
      int radius = (int) Math.ceil(border / 2.0 / 16.0);
      return Math.max(1, Math.min(radius, MAX_DEFAULT_PREGEN_RADIUS));
    }
    return 0;
  }

  /**
   * Indexes gamerules by a normalized name so camelCase config keys such as
   * {@code doDaylightCycle} match constants such as {@code DO_DAYLIGHT_CYCLE}.
   * Paper 26.2 no longer maps these through {@code GameRule.getByName}.
   */
  private static Map<String, GameRule<?>> gameRuleIndex() {
    Map<String, GameRule<?>> index = new HashMap<>();
    for (Field field : GameRule.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())) continue;
      if (!GameRule.class.isAssignableFrom(field.getType())) continue;
      try {
        GameRule<?> rule = (GameRule<?>) field.get(null);
        if (rule == null) continue;
        index.put(normalizeRule(field.getName()), rule);
        if (rule.getName() != null) {
          index.put(normalizeRule(rule.getName()), rule);
        }
      } catch (IllegalAccessException ignored) {
        // Skip inaccessible fields.
      }
    }
    return index;
  }

  private static String normalizeRule(String value) {
    return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
  }

  private static World.Environment parseEnvironment(String value) {
    try {
      return World.Environment.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (RuntimeException exception) {
      return World.Environment.NORMAL;
    }
  }

  private static WorldType parseWorldType(String value) {
    try {
      return WorldType.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (RuntimeException exception) {
      return WorldType.NORMAL;
    }
  }
}
