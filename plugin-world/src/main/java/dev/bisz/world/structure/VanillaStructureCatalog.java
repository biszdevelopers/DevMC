package dev.bisz.world.structure;

import dev.bisz.bundler.JSON;
import dev.bisz.world.storage.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Catalog of Minecraft's original structures, seeded from a bundled resource so
 * the admin structure book can list and place them even though vanilla
 * structure generation is disabled.
 */
public final class VanillaStructureCatalog {

  /** ServerData-relative catalog document. */
  public static final String FILE = "world/vanilla_structures.json";

  private final JavaPlugin plugin;
  private final Map<String, VanillaStructure> entries = new LinkedHashMap<>();

  public VanillaStructureCatalog(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  /** Installs the bundled defaults and loads every catalog entry. */
  public void load() {
    entries.clear();
    try (
      InputStream defaults = plugin.getResource("world/vanilla_structures.json")
    ) {
      if (defaults != null) JSON.mergeMissingDefaults(FILE, defaults);
    } catch (IOException exception) {
      plugin
        .getLogger()
        .warning(
          "Cannot install bundled vanilla structure defaults: " +
          exception.getMessage()
        );
    }
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    for (Map<String, Object> raw : Json.mapList(document, "structures")) {
      try {
        VanillaStructure entry = VanillaStructure.fromMap(raw);
        entries.put(entry.id(), entry);
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning(
            "Skipping malformed vanilla structure entry: " +
            exception.getMessage()
          );
      }
    }
  }

  public VanillaStructure get(String id) {
    return entries.get(id);
  }

  public Collection<VanillaStructure> all() {
    return List.copyOf(entries.values());
  }
}
