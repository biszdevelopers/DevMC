package dev.bisz.city.structure;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.bundler.JSON;
import dev.bisz.city.storage.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads structure definitions and resolves their schematic files. */
public final class StructureRegistry {

  /** ServerData-relative definition document. */
  public static final String FILE = "city/structures.json";

  private final JavaPlugin plugin;
  private final Path directory;
  private final Map<String, StructureDefinition> definitions =
    new LinkedHashMap<>();

  public StructureRegistry(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.directory = BundlerPlugin
      .instance()
      .jsonDatabase()
      .root()
      .resolve("city/structures");
  }

  /** Creates the structures directory and loads every definition. */
  public void load() {
    definitions.clear();
    try {
      Files.createDirectories(directory);
    } catch (IOException exception) {
      plugin
        .getLogger()
        .warning(
          "Cannot create structures directory " +
          directory +
          ": " +
          exception.getMessage()
        );
    }
    Map<String, Object> document = JSON.loadDataFromDataBase(FILE);
    for (Map<String, Object> raw : Json.mapList(document, "structures")) {
      try {
        StructureDefinition definition = StructureDefinition.fromMap(raw);
        definitions.put(definition.id(), definition);
      } catch (RuntimeException exception) {
        plugin
          .getLogger()
          .warning(
            "Skipping malformed structure definition: " +
            exception.getMessage()
          );
      }
    }
  }

  public StructureDefinition get(String id) {
    return definitions.get(id);
  }

  public Collection<StructureDefinition> all() {
    return List.copyOf(definitions.values());
  }

  /** The directory holding schematic files. */
  public Path directory() {
    return directory;
  }

  /** Resolves a definition's schematic file, confined to the directory. */
  public Path fileFor(StructureDefinition definition) {
    if (definition == null || definition.file() == null) return null;
    Path resolved = directory.resolve(definition.file()).normalize();
    if (!resolved.startsWith(directory)) {
      plugin
        .getLogger()
        .warning(
          "Structure " +
          definition.id() +
          " points outside the structures directory."
        );
      return null;
    }
    return resolved;
  }
}
