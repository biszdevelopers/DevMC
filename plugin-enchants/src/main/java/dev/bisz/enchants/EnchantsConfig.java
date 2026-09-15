package dev.bisz.enchants;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads the trueMC YAML configuration set from the plugin data folder, falling
 * back to the bundled resources for any missing file.
 */
final class EnchantsConfig {
  private final JavaPlugin plugin;

  private FileConfiguration config;
  private FileConfiguration categories;
  private FileConfiguration enchantments;
  private FileConfiguration sockets;
  private FileConfiguration costs;
  private FileConfiguration fishing;

  EnchantsConfig(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  void load() {
    config = load("config.yml");
    categories = load("categories.yml");
    enchantments = load("enchantments.yml");
    sockets = load("sockets.yml");
    costs = load("costs.yml");
    fishing = load("fishing.yml");
  }

  FileConfiguration config() { return config; }
  FileConfiguration categories() { return categories; }
  FileConfiguration enchantments() { return enchantments; }
  FileConfiguration sockets() { return sockets; }
  FileConfiguration costs() { return costs; }
  FileConfiguration fishing() { return fishing; }

  private FileConfiguration load(String name) {
    File file = new File(plugin.getDataFolder(), name);
    if (!file.exists()) {
      plugin.saveResource(name, false);
    }
    FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(file);
    InputStream defaultStream = plugin.getResource(name);
    if (defaultStream != null) {
      fileConfig.setDefaults(YamlConfiguration.loadConfiguration(
        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
    }
    return fileConfig;
  }
}
