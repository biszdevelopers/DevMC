package dev.bisz.city.wilderness;

import dev.bisz.bundler.JSON;
import dev.bisz.city.storage.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** Persisted resource and visibility indicators for every known region. */
public final class RegionIndicators {

  /** ServerData-relative wilderness indicator document. */
  public static final String FILE = "city/wilderness.json";

  private final JavaPlugin plugin;
  private final Map<String, RegionState> states = new LinkedHashMap<>();

  public RegionIndicators(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  /** Loads every tracked region from ServerData. */
  public void load() {
    states.clear();
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    for (Map<String, Object> raw : Json.mapList(document, "regions")) {
      try {
        RegionState state = RegionState.fromMap(raw);
        states.put(state.key().encode(), state);
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning(
            "Skipping malformed region entry: " + exception.getMessage()
          );
      }
    }
  }

  /** Persists every tracked region to ServerData. */
  public void save() {
    List<Map<String, Object>> encoded = new ArrayList<>();
    for (RegionState state : states.values()) encoded.add(state.toMap());
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schema", 1);
    document.put("regions", encoded);
    JSON.saveDataFromDataBase(FILE, document);
  }

  /** Returns the tracked region, creating it when absent. */
  public RegionState getOrCreate(RegionKey key, long now) {
    return states.computeIfAbsent(
      key.encode(),
      ignored -> new RegionState(key, now)
    );
  }

  /** Returns the tracked region, or null when absent. */
  public RegionState get(RegionKey key) {
    return states.get(key.encode());
  }

  /** Every tracked region. */
  public Collection<RegionState> all() {
    return List.copyOf(states.values());
  }

  /** Removes a region that no longer needs tracking. */
  public void remove(RegionKey key) {
    states.remove(key.encode());
  }
}
