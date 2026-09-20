package dev.bisz.world.settlement;

import dev.bisz.bundler.JSON;
import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.ZoneType;
import dev.bisz.world.storage.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns the set of settlements and resolves land zones. */
public final class SettlementManager {

  /** ServerData-relative settlement document. */
  public static final String FILE = "world/settlements.json";

  private final JavaPlugin plugin;
  private final WorldSettings settings;
  private final Map<String, Settlement> settlements = new LinkedHashMap<>();
  private final ChunkIndex index = new ChunkIndex();

  public SettlementManager(JavaPlugin plugin, WorldSettings settings) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** Loads every settlement from ServerData. */
  public void load() {
    settlements.clear();
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    for (Map<String, Object> raw : Json.mapList(document, "settlements")) {
      try {
        Settlement settlement = Settlement.fromMap(raw);
        settlements.put(settlement.id(), settlement);
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning("Skipping malformed settlement entry: " + exception.getMessage());
      }
    }
    index.rebuild(settlements.values());
  }

  /** Persists every settlement to ServerData. */
  public void save() {
    List<Map<String, Object>> encoded = new ArrayList<>();
    for (Settlement settlement : settlements.values()) encoded.add(settlement.toMap());
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schema", 1);
    document.put("settlements", encoded);
    JSON.saveDataFromDataBase(FILE, document);
  }

  public Collection<Settlement> all() {
    return List.copyOf(settlements.values());
  }

  public Settlement get(String id) {
    return settlements.get(id);
  }

  /** Finds the settlement whose name matches, ignoring case. */
  public Settlement byName(String name) {
    for (Settlement settlement : settlements.values()) {
      if (settlement.name().equalsIgnoreCase(name)) return settlement;
    }
    return null;
  }

  /** Creates a settlement seeded with the chunk containing the origin location. */
  public Settlement create(String name, Location origin) {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(origin.getWorld(), "origin world");
    String id = uniqueId(name);
    Settlement settlement = new Settlement(
      id,
      name,
      origin.getWorld().getName(),
      settings.rentDefaultPrice(),
      settings.rentDefaultPeriodMillis(),
      settings.plotSize()
    );
    settlement.spawn(origin);
    settlement.chunks().add(ChunkKey.of(origin));
    settlements.put(id, settlement);
    index.rebuild(settlements.values());
    save();
    return settlement;
  }

  /** Deletes a settlement and returns whether it existed. */
  public boolean delete(String id) {
    Settlement removed = settlements.remove(id);
    if (removed == null) return false;
    index.rebuild(settlements.values());
    save();
    return true;
  }

  /** Adds a chunk to a settlement, returning false when already present. */
  public boolean addChunk(Settlement settlement, ChunkKey key) {
    Objects.requireNonNull(settlement, "settlement");
    boolean added = settlement.chunks().add(Objects.requireNonNull(key, "key"));
    if (added) {
      index.rebuild(settlements.values());
      save();
    }
    return added;
  }

  /** Removes a chunk from a settlement, returning false when not present. */
  public boolean removeChunk(Settlement settlement, ChunkKey key) {
    Objects.requireNonNull(settlement, "settlement");
    boolean removed = settlement.chunks().remove(Objects.requireNonNull(key, "key"));
    if (removed) {
      index.rebuild(settlements.values());
      save();
    }
    return removed;
  }

  /** The settlement containing the location, or null. */
  public Settlement settlementAt(Location location) {
    if (location == null || location.getWorld() == null) return null;
    String id = index.settlementIdFor(ChunkKey.of(location));
    return id == null ? null : settlements.get(id);
  }

  /** The settlement owning a chunk, or null. */
  public Settlement settlementAt(ChunkKey key) {
    String id = index.settlementIdFor(key);
    return id == null ? null : settlements.get(id);
  }

  /** Whether the chunk belongs to any settlement. */
  public boolean isSettlement(ChunkKey key) {
    return index.isSettlement(key);
  }

  /**
   * Resolves the zone at a location, or null when the world is not managed by
   * settlement rules.
   */
  public ZoneType zoneAt(Location location) {
    if (location == null || location.getWorld() == null) return null;
    Settlement settlement = settlementAt(location);
    if (settlement != null) {
      return settlement.policeEnabled() && !settlement.pvpAllowed()
        ? ZoneType.POLICED
        : ZoneType.UNMONITORED;
    }
    if (settings.managesWorld(location.getWorld().getName())) {
      return ZoneType.WILDERNESS;
    }
    return null;
  }

  /** Whether the supplied world is governed by settlement and wilderness rules. */
  public boolean isManagedWorld(String worldName) {
    return settings.managesWorld(worldName);
  }

  private String uniqueId(String name) {
    String base = name
      .toLowerCase(Locale.ROOT)
      .replaceAll("[^a-z0-9]+", "-")
      .replaceAll("(^-|-$)", "");
    if (base.isEmpty()) base = "settlement";
    String candidate = base;
    int suffix = 2;
    while (settlements.containsKey(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
  }
}
