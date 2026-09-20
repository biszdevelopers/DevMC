package dev.bisz.worldgen.snapshot;

import dev.bisz.bundler.JSON;
import dev.bisz.worldgen.storage.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** Persists named chunk snapshots under ServerData. */
public final class SnapshotStore {

  /** ServerData-relative snapshot document. */
  public static final String FILE = "worldgen/snapshots.json";

  private final JavaPlugin plugin;
  private final Map<String, ChunkSnapshot> snapshots = new LinkedHashMap<>();

  public SnapshotStore(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  /** Loads every stored snapshot. */
  public void load() {
    snapshots.clear();
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    Map<String, Object> entries = Json.map(document.get("snapshots"));
    if (entries == null) return;
    for (Map.Entry<String, Object> entry : entries.entrySet()) {
      Map<String, Object> raw = Json.map(entry.getValue());
      if (raw == null) continue;
      try {
        snapshots.put(entry.getKey(), ChunkSnapshot.fromMap(raw));
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning(
            "Skipping malformed snapshot " +
            entry.getKey() +
            ": " +
            exception.getMessage()
          );
      }
    }
  }

  /** Persists every stored snapshot. */
  public void save() {
    Map<String, Object> entries = new LinkedHashMap<>();
    for (Map.Entry<String, ChunkSnapshot> entry : snapshots.entrySet()) {
      entries.put(entry.getKey(), entry.getValue().toMap());
    }
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schema", 1);
    document.put("snapshots", entries);
    JSON.saveDataFromDataBase(FILE, document);
  }

  /** Stores or replaces a snapshot and persists immediately. */
  public void put(String name, ChunkSnapshot snapshot) {
    snapshots.put(name, snapshot);
    save();
  }

  public ChunkSnapshot get(String name) {
    return snapshots.get(name);
  }

  public boolean has(String name) {
    return snapshots.containsKey(name);
  }

  public List<String> names() {
    return List.copyOf(snapshots.keySet());
  }

  /** Removes a snapshot, returning whether it existed. */
  public boolean remove(String name) {
    boolean removed = snapshots.remove(name) != null;
    if (removed) save();
    return removed;
  }
}
