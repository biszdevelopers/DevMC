package dev.bisz.world.setup;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.worldgen.RustMapGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * One-shot, idempotent world initialization: create or adopt the world, apply
 * gamerules and difficulty, set the spawn and world border, found the spawn
 * settlement, and optionally pre-generate chunks.
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

  /**
   * Runtime overrides supplied by the setup dialog. Blank names/seeds and a
   * non-positive border fall back to the configured defaults.
   */
  public record SetupRequest(String worldName, String seed, double borderSize) {
    /** All-blank request, equivalent to using the configured defaults. */
    public static final SetupRequest DEFAULT = new SetupRequest("", "", 0.0);
  }

  private final WorldPlugin plugin;

  public SetupService(WorldPlugin plugin) {
    this.plugin = plugin;
  }

  /** Runs the full setup against the world implied by the reference location. */
  public Report run(Location reference) {
    return run(reference, SetupRequest.DEFAULT);
  }

  /** Runs setup using the request's overrides where provided. */
  public Report run(Location reference, SetupRequest request) {
    SetupRequest effective = request == null ? SetupRequest.DEFAULT : request;
    List<Step> steps = new ArrayList<>();
    World world = resolveWorld(reference, effective, steps);
    if (world == null) return new Report(List.copyOf(steps));
    applyGamerules(world, steps);
    applyDifficulty(world, steps);
    Location spawn = applySpawn(world, reference, steps);
    applyBorder(world, spawn, effective, steps);
    applySettlement(spawn, steps);
    applyPregen(world, steps);
    applyStructures(world, steps);
    return new Report(List.copyOf(steps));
  }

  private World resolveWorld(
    Location reference,
    SetupRequest request,
    List<Step> steps
  ) {
    String name = request.worldName();
    if (name == null || name.isBlank()) {
      name = plugin.settings().setupWorldName();
    }
    if (name == null || name.isBlank()) {
      World world = reference.getWorld();
      if (world == null) {
        steps.add(new Step("world", false, "No world context."));
        return null;
      }
      steps.add(new Step("world", true, "Using " + world.getName()));
      return world;
    }
    World existing = Bukkit.getWorld(name);
    if (existing != null) {
      steps.add(new Step("world", true, "Found " + name));
      return existing;
    }
    WorldCreator creator = new WorldCreator(name)
      .environment(parseEnvironment(plugin.settings().setupEnvironment()))
      .type(parseWorldType(plugin.settings().setupWorldType()))
      .generateStructures(plugin.settings().setupGenerateStructures());
    if (plugin.settings().rustMapEnabled()) {
      creator.generator(
        new RustMapGenerator(
          plugin.settings().rustMapIslandRadius(),
          plugin.settings().rustMapSeaLevel(),
          plugin.settings().rustMapBiomes()
        )
      );
    }
    String seed = request.seed();
    if (seed == null || seed.isBlank()) {
      seed = plugin.settings().setupSeed();
    }
    if (seed != null && !seed.isBlank()) {
      try {
        creator.seed(Long.parseLong(seed.trim()));
      } catch (NumberFormatException exception) {
        creator.seed(seed.hashCode());
      }
    }
    World created = creator.createWorld();
    if (created == null) {
      steps.add(new Step("world", false, "Could not create " + name));
      return null;
    }
    steps.add(new Step("world", true, "Created " + name));
    return created;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void applyGamerules(World world, List<Step> steps) {
    int applied = 0;
    int skipped = 0;
    for (Map.Entry<String, String> entry : plugin
      .settings()
      .setupGamerules()
      .entrySet()) {
      GameRule rule = GameRule.getByName(entry.getKey());
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
    if (reference.getWorld() == null || !reference.getWorld().equals(world)) {
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
    SetupRequest request,
    List<Step> steps
  ) {
    double size = request.borderSize() > 0.0
      ? request.borderSize()
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
    Settlement settlement = plugin.settlements().create(plugin.settings().setupSettlementName(), spawn);
    steps.add(new Step("settlement", true, "Created " + settlement.name()));
  }

  private void applyPregen(World world, List<Step> steps) {
    int radius = plugin.settings().pregenRadiusChunks();
    if (radius <= 0) {
      steps.add(new Step("pregen", true, "Skipped"));
      return;
    }
    plugin.pregenerator().start(world, radius, null);
    steps.add(new Step("pregen", true, "Started radius " + radius));
  }

  private void applyStructures(World world, List<Step> steps) {
    if (!plugin.settings().rustMapEnabled()) {
      steps.add(new Step("structures", true, "Skipped"));
      return;
    }
    int placed = plugin
      .structurePlacer()
      .plantMonuments(world, plugin.settings().rustMapStructureRadius());
    steps.add(
      new Step(
        "structures",
        true,
        placed + " placed, " +
        plugin.structures().registry().all().size() + " defined"
      )
    );
  }

  private static World.Environment parseEnvironment(String value) {
    try {
      return World.Environment.valueOf(
        value.toUpperCase(Locale.ROOT)
      );
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
