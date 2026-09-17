package dev.bisz.city.city;

import dev.bisz.bundler.JSON;
import dev.bisz.city.config.CitySettings;
import dev.bisz.city.model.ChunkKey;
import dev.bisz.city.model.City;
import dev.bisz.city.model.ZoneType;
import dev.bisz.city.storage.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns the set of cities and resolves land zones. */
public final class CityManager {

  /** ServerData-relative city document. */
  public static final String FILE = "city/cities.json";

  private final JavaPlugin plugin;
  private final CitySettings settings;
  private final Map<String, City> cities = new LinkedHashMap<>();
  private final ChunkIndex index = new ChunkIndex();

  public CityManager(JavaPlugin plugin, CitySettings settings) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** Loads every city from ServerData. */
  public void load() {
    cities.clear();
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    for (Map<String, Object> raw : Json.mapList(document, "cities")) {
      try {
        City city = City.fromMap(raw);
        cities.put(city.id(), city);
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning("Skipping malformed city entry: " + exception.getMessage());
      }
    }
    index.rebuild(cities.values());
  }

  /** Persists every city to ServerData. */
  public void save() {
    List<Map<String, Object>> encoded = new ArrayList<>();
    for (City city : cities.values()) encoded.add(city.toMap());
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schema", 1);
    document.put("cities", encoded);
    JSON.saveDataFromDataBase(FILE, document);
  }

  public Collection<City> all() {
    return List.copyOf(cities.values());
  }

  public City get(String id) {
    return cities.get(id);
  }

  /** Finds the city whose name matches, ignoring case. */
  public City byName(String name) {
    for (City city : cities.values()) {
      if (city.name().equalsIgnoreCase(name)) return city;
    }
    return null;
  }

  /** Creates a city seeded with the chunk containing the origin location. */
  public City create(String name, Location origin) {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(origin.getWorld(), "origin world");
    String id = uniqueId(name);
    City city = new City(
      id,
      name,
      origin.getWorld().getName(),
      settings.rentDefaultPrice(),
      settings.rentDefaultPeriodMillis(),
      settings.plotSize()
    );
    city.spawn(origin);
    city.chunks().add(ChunkKey.of(origin));
    cities.put(id, city);
    index.rebuild(cities.values());
    save();
    return city;
  }

  /** Deletes a city and returns whether it existed. */
  public boolean delete(String id) {
    City removed = cities.remove(id);
    if (removed == null) return false;
    index.rebuild(cities.values());
    save();
    return true;
  }

  /** Adds a chunk to a city, returning false when already present. */
  public boolean addChunk(City city, ChunkKey key) {
    Objects.requireNonNull(city, "city");
    boolean added = city.chunks().add(Objects.requireNonNull(key, "key"));
    if (added) {
      index.rebuild(cities.values());
      save();
    }
    return added;
  }

  /** Removes a chunk from a city, returning false when not present. */
  public boolean removeChunk(City city, ChunkKey key) {
    Objects.requireNonNull(city, "city");
    boolean removed = city.chunks().remove(Objects.requireNonNull(key, "key"));
    if (removed) {
      index.rebuild(cities.values());
      save();
    }
    return removed;
  }

  /** The city containing the location, or null. */
  public City cityAt(Location location) {
    if (location == null || location.getWorld() == null) return null;
    String id = index.cityIdFor(ChunkKey.of(location));
    return id == null ? null : cities.get(id);
  }

  /** The city owning a chunk, or null. */
  public City cityAt(ChunkKey key) {
    String id = index.cityIdFor(key);
    return id == null ? null : cities.get(id);
  }

  /** Whether the chunk belongs to any city. */
  public boolean isCity(ChunkKey key) {
    return index.isCity(key);
  }

  /**
   * Resolves the zone at a location, or null when the world is not managed by
   * city rules.
   */
  public ZoneType zoneAt(Location location) {
    if (location == null || location.getWorld() == null) return null;
    City city = cityAt(location);
    if (city != null) {
      return city.policeEnabled() && !city.pvpAllowed()
        ? ZoneType.POLICED
        : ZoneType.UNMONITORED;
    }
    if (settings.managesWorld(location.getWorld().getName())) {
      return ZoneType.WILDERNESS;
    }
    return null;
  }

  /** Whether the supplied world is governed by city and wilderness rules. */
  public boolean isManagedWorld(String worldName) {
    return settings.managesWorld(worldName);
  }

  private String uniqueId(String name) {
    String base = name
      .toLowerCase(Locale.ROOT)
      .replaceAll("[^a-z0-9]+", "-")
      .replaceAll("(^-|-$)", "");
    if (base.isEmpty()) base = "city";
    String candidate = base;
    int suffix = 2;
    while (cities.containsKey(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
  }
}
