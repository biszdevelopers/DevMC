package dev.bisz.city.structure;

import dev.bisz.bundler.JSON;
import dev.bisz.city.config.CitySettings;
import dev.bisz.city.loot.LootTables;
import dev.bisz.city.model.LootTable;
import dev.bisz.city.storage.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Backbone for custom structures: load schematics, paste them, fill loot, and
 * run Rust-like event lifecycles. Gameplay details live in registered
 * {@link StructureEventHook}s.
 */
public final class StructureService {

  /** ServerData-relative instance document. */
  public static final String FILE = "city/structure_instances.json";

  private final JavaPlugin plugin;
  private final CitySettings settings;
  private final LootTables lootTables;
  private final StructureRegistry registry;
  private final StructureBridge bridge;
  private final Map<String, StructureInstance> instances = new LinkedHashMap<>();
  private final Map<String, List<StructureEventHook>> hooks = new HashMap<>();
  private final Random random = new Random();

  public StructureService(
    JavaPlugin plugin,
    CitySettings settings,
    LootTables lootTables
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.lootTables = Objects.requireNonNull(lootTables, "lootTables");
    this.registry = new StructureRegistry(plugin);
    this.bridge = StructureBridges.create(plugin);
  }

  /** Loads definitions and placed instances. */
  public void load() {
    registry.load();
    instances.clear();
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    for (Map<String, Object> raw : Json.mapList(document, "instances")) {
      try {
        StructureInstance instance = StructureInstance.fromMap(raw);
        instances.put(instance.instanceId(), instance);
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning(
            "Skipping malformed structure instance: " + exception.getMessage()
          );
      }
    }
  }

  /** Persists placed instances. */
  public void save() {
    List<Map<String, Object>> encoded = new ArrayList<>();
    for (StructureInstance instance : instances.values()) {
      encoded.add(instance.toMap());
    }
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schema", 1);
    document.put("instances", encoded);
    JSON.saveDataFromDataBase(FILE, document);
  }

  /** Reloads definitions and clears the loaded-schematic cache. */
  public void reload() {
    registry.load();
  }

  public StructureRegistry registry() {
    return registry;
  }

  public StructureBridge bridge() {
    return bridge;
  }

  public Collection<StructureInstance> instances() {
    return List.copyOf(instances.values());
  }

  public StructureInstance instance(String instanceId) {
    return instances.get(instanceId);
  }

  /** Registers an event hook for its declared type. */
  public void registerHook(StructureEventHook hook) {
    Objects.requireNonNull(hook, "hook");
    hooks
      .computeIfAbsent(hook.type(), ignored -> new ArrayList<>())
      .add(hook);
  }

  /**
   * Pastes a structure at a location and tracks it.
   *
   * @param eventId optional event identifier recorded on the instance
   * @return the placed instance, or null when pasting is unavailable
   */
  public StructureInstance spawn(
    String definitionId,
    Location location,
    String eventId
  ) {
    if (!settings.structuresEnabled()) return null;
    Objects.requireNonNull(location, "location");
    World world = location.getWorld();
    if (world == null) return null;
    StructureDefinition definition = registry.get(definitionId);
    if (definition == null) {
      plugin
        .getLogger()
        .warning("No structure definition named " + definitionId + ".");
      return null;
    }
    if (!bridge.available()) {
      plugin
        .getLogger()
        .warning("Cannot paste " + definitionId + ": no structure backend.");
      return null;
    }
    Path file = registry.fileFor(definition);
    if (file == null || !Files.isRegularFile(file)) {
      plugin
        .getLogger()
        .warning(
          "Structure " +
          definitionId +
          " is missing schematic " +
          definition.file() +
          "."
        );
      return null;
    }
    Object handle = bridge.load(definition, file);
    if (handle == null) {
      plugin
        .getLogger()
        .warning("Failed to read schematic for " + definitionId + ".");
      return null;
    }
    PlacedStructure bounds = bridge.paste(
      handle,
      world,
      location,
      definition.rotationY(),
      definition.ignoreAir(),
      definition.copyEntities()
    );
    if (bounds == null) {
      plugin
        .getLogger()
        .warning("Failed to paste structure " + definitionId + ".");
      return null;
    }
    StructureInstance instance = StructureInstance.create(
      definition.id(),
      eventId,
      bounds,
      System.currentTimeMillis()
    );
    if (definition.lootTableId() != null) refill(instance, definition);
    instances.put(instance.instanceId(), instance);
    save();
    for (StructureEventHook hook : hooksFor(definition)) {
      hook.onSpawn(instance, definition);
    }
    return instance;
  }

  /** Removes an instance, optionally clearing its blocks. */
  public boolean despawn(String instanceId, boolean clear) {
    StructureInstance instance = instances.remove(instanceId);
    if (instance == null) return false;
    StructureDefinition definition = registry.get(instance.definitionId());
    if (definition != null) {
      for (StructureEventHook hook : hooksFor(definition)) {
        hook.onDespawn(instance, definition);
      }
    }
    if (clear && bridge.available()) {
      World world = Bukkit.getWorld(instance.bounds().world());
      if (world != null) bridge.clear(world, instance.bounds());
    }
    save();
    return true;
  }

  /** Refills loot, fires activity, and evaluates despawn for every instance. */
  public void tick(long now) {
    for (StructureInstance instance : new ArrayList<>(instances.values())) {
      StructureDefinition definition = registry.get(instance.definitionId());
      if (definition == null) continue;
      if (
        definition.lootRespawnTicks() > 0L &&
        now - instance.lastLootedAt() >= definition.lootRespawnTicks() * 50L
      ) {
        refill(instance, definition);
        save();
      }
      List<StructureEventHook> typeHooks = hooksFor(definition);
      for (StructureEventHook hook : typeHooks) {
        hook.onActive(instance, definition, now);
      }
      if (definition.persistent()) continue;
      for (StructureEventHook hook : typeHooks) {
        if (hook.shouldDespawn(instance, definition, now)) {
          despawn(instance.instanceId(), settings.structuresClearOnDespawn());
          break;
        }
      }
    }
  }

  /** Notifies hooks that a player opened a container inside an instance. */
  public void notifyLoot(StructureInstance instance, Player player) {
    if (instance == null || player == null) return;
    StructureDefinition definition = registry.get(instance.definitionId());
    if (definition == null) return;
    for (StructureEventHook hook : hooksFor(definition)) {
      hook.onLoot(instance, definition, player);
    }
  }

  /** The instance containing a block position, or null. */
  public StructureInstance instanceAt(String world, int x, int y, int z) {
    for (StructureInstance instance : instances.values()) {
      if (instance.contains(world, x, y, z)) return instance;
    }
    return null;
  }

  private int refill(StructureInstance instance, StructureDefinition definition) {
    LootTable table = lootTables.get(definition.lootTableId());
    if (table == null) return 0;
    World world = Bukkit.getWorld(instance.bounds().world());
    if (world == null) return 0;
    int filled = StructureLoot.fill(
      world,
      instance.bounds(),
      table,
      random
    );
    instance.lastLootedAt(System.currentTimeMillis());
    return filled;
  }

  private List<StructureEventHook> hooksFor(StructureDefinition definition) {
    if (definition.eventType() == null) return List.of();
    return hooks.getOrDefault(definition.eventType(), List.of());
  }
}
