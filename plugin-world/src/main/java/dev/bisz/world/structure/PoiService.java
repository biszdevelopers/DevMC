package dev.bisz.world.structure;

import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.loot.LootTables;
import dev.bisz.world.model.LootTable;
import dev.bisz.world.wilderness.SmallStructures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Places small, destructible, regenerable points of interest (POIs) from
 * schematics during the feature pass. POIs are kept within a chunk so the next
 * regeneration cleanly erases them; there is no persistent instance tracking.
 *
 * <p>Large landmarks (monuments) are not handled here; administrators define
 * them manually.
 */
public final class PoiService {

  private final WorldSettings settings;
  private final LootTables lootTables;
  private final SmallStructures builtin;
  private final StructureRegistry registry;
  private final StructureBridge bridge;
  private final Set<String> live = new HashSet<>();

  public PoiService(
    JavaPlugin plugin,
    WorldSettings settings,
    LootTables lootTables,
    SmallStructures builtin
  ) {
    Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.lootTables = Objects.requireNonNull(lootTables, "lootTables");
    this.builtin = Objects.requireNonNull(builtin, "builtin");
    this.registry = new StructureRegistry(plugin);
    this.bridge = StructureBridges.create(plugin);
  }

  /** Loads the POI definitions. */
  public void load() {
    registry.load();
  }

  /** Reloads the POI definitions. */
  public void reload() {
    registry.load();
  }

  public StructureRegistry registry() {
    return registry;
  }

  public StructureBridge bridge() {
    return bridge;
  }

  /** Every registered POI definition. */
  public Collection<StructureDefinition> definitions() {
    return registry.all();
  }

  /** Number of currently tracked live POIs. */
  public int liveCount() {
    return live.size();
  }

  /**
   * Rolls and places a POI in this chunk, if any.
   *
   * @return the number of POIs placed, used as part of the resource baseline
   */
  public int reseed(World world, int chunkX, int chunkZ, Random random) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(random, "random");
    String key = world.getName() + ":" + chunkX + ":" + chunkZ;
    live.remove(key);
    if (!settings.structuresEnabled()) return 0;
    double chance = settings.smallStructureChance();
    if (chance <= 0.0) return 0;
    if (random.nextDouble() > chance) return 0;
    if (settings.poiCap() > 0 && live.size() >= settings.poiCap()) return 0;
    if (pasteRandom(world, chunkX, chunkZ, random)) {
      live.add(key);
      return 1;
    }
    builtin.place(world, chunkX, chunkZ, random);
    live.add(key);
    return 1;
  }

  private boolean pasteRandom(
    World world,
    int chunkX,
    int chunkZ,
    Random random
  ) {
    List<StructureDefinition> definitions = new ArrayList<>(registry.all());
    if (definitions.isEmpty() || !bridge.available()) return false;
    StructureDefinition definition = definitions.get(
      random.nextInt(definitions.size())
    );
    Path file = registry.fileFor(definition);
    if (file == null || !Files.isRegularFile(file)) return false;
    Object handle = bridge.load(definition, file);
    if (handle == null) return false;
    int x = (chunkX << 4) + random.nextInt(16);
    int z = (chunkZ << 4) + random.nextInt(16);
    int y = world.getHighestBlockYAt(x, z) + 1;
    PlacedStructure bounds = bridge.paste(
      handle,
      world,
      new Location(world, x + 0.5, y, z + 0.5),
      definition.rotationY(),
      definition.ignoreAir(),
      definition.copyEntities()
    );
    if (bounds == null) return false;
    if (definition.lootTableId() != null) {
      LootTable table = lootTables.get(definition.lootTableId());
      if (table != null) StructureLoot.fill(world, bounds, table, random);
    }
    return true;
  }
}
