/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.catalogs.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.catalogs.StaticCatalogService;
import dev.bisz.catalogs.internal.LocaleCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.bukkit.plugin.java.JavaPlugin;

public final class JsonCatalogService implements StaticCatalogService {

  private final JavaPlugin plugin;
  private final ObjectMapper mapper = new ObjectMapper().configure(
    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
    true
  );
  private volatile Map<String, Map<String, String>> locales = Map.of();

  public JsonCatalogService(JavaPlugin plugin) {
    this.plugin = plugin;
    this.copyDefaults();
    this.reload();
  }

  @Override
  public void reload() {
    Path directory = this.plugin.getDataFolder()
      .toPath()
      .resolve("catalogs/locales");
    HashMap<String, Map<String, String>> replacement = new HashMap<
      String,
      Map<String, String>
    >();
    try (Stream<Path> files = Files.list(directory)) {
      for (Path file : files
        .filter(path -> path.getFileName().toString().endsWith(".json"))
        .toList()) {
        LocaleCatalog catalog = this.mapper.readValue(
          file.toFile(),
          LocaleCatalog.class
        );
        if (
          catalog.schemaVersion() != 1 ||
          catalog.language() == null ||
          catalog.translations() == null
        ) {
          throw new IllegalArgumentException(
            "Invalid locale catalog: " + String.valueOf(file.getFileName())
          );
        }
        replacement.put(
          catalog.language().toLowerCase(),
          Map.copyOf(catalog.translations())
        );
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load static catalogs", exception);
    }
    if (!replacement.containsKey("zh_cn")) {
      throw new IllegalStateException("A zh_cn locale catalog is required");
    }
    this.locales = Map.copyOf(replacement);
  }

  @Override
  public String translation(String language, String key) {
    Map<String, String> selected = this.locales.getOrDefault(
      language.toLowerCase(),
      this.locales.get("zh_cn")
    );
    return selected.getOrDefault(
      key,
      this.locales.get("zh_cn").getOrDefault(key, key)
    );
  }

  private void copyDefaults() {
    Path directory = this.plugin.getDataFolder()
      .toPath()
      .resolve("catalogs/locales");
    try {
      Files.createDirectories(directory, new FileAttribute[0]);
      for (String locale : new String[] { "zh_cn", "en_us" }) {
        Path target = directory.resolve(locale + ".json");
        if (Files.exists(target, new LinkOption[0])) continue;
        String resource = "catalogs/locales/" + locale + ".json";
        InputStream bundled = this.plugin instanceof BundlerPlugin bundler
          ? bundler.bundledResource(resource)
          : this.plugin.getResource(resource);
        try (InputStream input = bundled) {
          if (input == null) {
            throw new IllegalStateException("Missing bundled locale " + locale);
          }
          Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
        "Cannot create static catalog directory",
        exception
      );
    }
  }
}
