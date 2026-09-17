package dev.bisz.city.loot;

import dev.bisz.bundler.JSON;
import dev.bisz.city.model.LootTable;
import dev.bisz.city.storage.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads the weighted loot tables used by monuments and wilderness caches. */
public final class LootTables {

  /** ServerData-relative loot document. */
  public static final String FILE = "city/loot.json";

  private final Map<String, LootTable> tables;

  private LootTables(Map<String, LootTable> tables) {
    this.tables = Map.copyOf(tables);
  }

  /** Installs defaults into ServerData and loads every table. */
  public static LootTables load(JavaPlugin plugin) {
    try (InputStream defaults = plugin.getResource("city/loot.json")) {
      if (defaults == null) {
        throw new IllegalStateException("Missing bundled city loot defaults");
      }
      JSON.mergeMissingDefaults(FILE, defaults);
    } catch (IOException exception) {
      throw new IllegalStateException(
        "Cannot close bundled city loot defaults",
        exception
      );
    }
    return from(JSON.loadDataFromDataBase(FILE));
  }

  /** Creates a table set from an in-memory document, for tests. */
  public static LootTables from(Map<String, Object> document) {
    Map<String, LootTable> parsed = new LinkedHashMap<>();
    for (Map<String, Object> raw : Json.mapList(document, "tables")) {
      LootTable table = LootTable.fromMap(raw);
      parsed.put(table.id(), table);
    }
    return new LootTables(parsed);
  }

  /** Returns the table with the supplied id, or null when absent. */
  public LootTable get(String id) {
    return tables.get(id);
  }

  /** Whether a table with the supplied id exists. */
  public boolean has(String id) {
    return tables.containsKey(id);
  }

  /** All loaded tables keyed by id. */
  public Map<String, LootTable> all() {
    return tables;
  }
}
