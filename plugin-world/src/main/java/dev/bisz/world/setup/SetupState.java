package dev.bisz.world.setup;

import dev.bisz.bundler.JSON;
import dev.bisz.world.storage.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persisted world-generation state: whether first-time setup has completed and
 * which world it produced.
 */
public final class SetupState {

  /** ServerData-relative state document. */
  public static final String FILE = "world/state.json";

  private final JavaPlugin plugin;
  private boolean complete;
  private String worldName = "";

  public SetupState(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  /** Loads the persisted state. */
  public void load() {
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    this.complete = Json.bool(document, "setup_complete", false);
    this.worldName = Json.string(document, "world_name", "");
  }

  /** Persists the current state. */
  public void save() {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schema", 1);
    document.put("setup_complete", complete);
    document.put("world_name", worldName);
    JSON.saveDataFromDataBase(FILE, document);
  }

  /** Whether first-time setup has completed. */
  public boolean complete() {
    return complete;
  }

  /** The world produced by setup, or blank when unknown. */
  public String worldName() {
    return worldName;
  }

  /** Marks setup complete and persists the state. */
  public void markComplete(String world) {
    this.complete = true;
    this.worldName = world == null ? "" : world;
    save();
    plugin.getLogger().info("World setup marked complete.");
  }

  /** Clears the completion flag, for re-running setup. */
  public void reset() {
    this.complete = false;
    this.worldName = "";
    save();
  }
}
