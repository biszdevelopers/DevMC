package dev.bisz.enchants;

import dev.bisz.bundler.JSON;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads the trueMC JSON configuration set from Bundler's ServerData store,
 * falling back to the bundled resources for any missing file.
 */
final class EnchantsConfig {
  static final String CONFIG_FILE = "enchants/config.json";
  static final String COSTS_FILE = "enchants/costs.json";
  static final String FISHING_FILE = "enchants/fishing.json";

  private final EnchantsPlugin plugin;

  private Map<String, Object> config = Map.of();
  private Map<String, Object> costs = Map.of();
  private Map<String, Object> fishing = Map.of();

  EnchantsConfig(EnchantsPlugin plugin) {
    this.plugin = plugin;
  }

  void load() {
    config = load(CONFIG_FILE, "config.json");
    costs = load(COSTS_FILE, "costs.json");
    fishing = load(FISHING_FILE, "fishing.json");
  }

  Map<String, Object> config() { return config; }
  Map<String, Object> costs() { return costs; }
  Map<String, Object> fishing() { return fishing; }

  private Map<String, Object> load(String databasePath, String resource) {
    try (InputStream defaults = plugin.getResource(resource)) {
      if (defaults == null) throw new IllegalStateException("Missing bundled enchants default " + resource);
      JSON.mergeMissingDefaults(databasePath, defaults);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot close bundled enchants default " + resource, exception);
    }
    return JSON.loadDataFromDataBase(databasePath);
  }

  static Object get(Map<String, Object> values, String path) {
    Object current = values;
    for (String key : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) return null;
      current = map.get(key);
    }
    return current;
  }

  static boolean bool(Map<String, Object> values, String path, boolean fallback) {
    return get(values, path) instanceof Boolean flag ? flag : fallback;
  }

  static int integer(Map<String, Object> values, String path, int fallback) {
    return get(values, path) instanceof Number number ? number.intValue() : fallback;
  }

  static double decimal(Map<String, Object> values, String path, double fallback) {
    return get(values, path) instanceof Number number ? number.doubleValue() : fallback;
  }

  static String string(Map<String, Object> values, String path, String fallback) {
    return get(values, path) instanceof String text ? text : fallback;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> section(Map<String, Object> values, String path) {
    return get(values, path) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> maps(Map<String, Object> values, String path) {
    return get(values, path) instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }
}
