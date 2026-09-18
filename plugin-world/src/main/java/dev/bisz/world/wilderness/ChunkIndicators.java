package dev.bisz.world.wilderness;

import dev.bisz.bundler.JSON;
import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.storage.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** Persisted resource and visibility indicators for every tracked chunk. */
public final class ChunkIndicators {

  /** ServerData-relative wilderness indicator document. */
  public static final String FILE = "world/wilderness.json";

  private final JavaPlugin plugin;
  private final Map<String, ChunkState> states = new LinkedHashMap<>();

  public ChunkIndicators(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  /** Loads every tracked chunk from ServerData. */
  public void load() {
    states.clear();
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    for (Map<String, Object> raw : Json.mapList(document, "chunks")) {
      try {
        ChunkState state = ChunkState.fromMap(raw);
        states.put(state.key().encode(), state);
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning(
            "Skipping malformed chunk indicator entry: " +
            exception.getMessage()
          );
      }
    }
  }

  /** Persists every tracked chunk to ServerData. */
  public void save() {
    List<Map<String, Object>> encoded = new ArrayList<>();
    for (ChunkState state : states.values()) encoded.add(state.toMap());
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schema", 2);
    document.put("chunks", encoded);
    JSON.saveDataFromDataBase(FILE, document);
  }

  /** Returns the tracked chunk, creating it when absent. */
  public ChunkState getOrCreate(ChunkKey key, long now) {
    return states.computeIfAbsent(
      key.encode(),
      ignored -> new ChunkState(key, now)
    );
  }

  /** Returns the tracked chunk, or null when absent. */
  public ChunkState get(ChunkKey key) {
    return states.get(key.encode());
  }

  /** Every tracked chunk. */
  public Collection<ChunkState> all() {
    return List.copyOf(states.values());
  }

  /** Removes a chunk that no longer needs tracking. */
  public void remove(ChunkKey key) {
    states.remove(key.encode());
  }
}
