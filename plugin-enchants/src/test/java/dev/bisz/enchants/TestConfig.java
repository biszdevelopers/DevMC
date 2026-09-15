package dev.bisz.enchants;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;

/** Loads the bundled trueMC YAML defaults for unit tests without a running server. */
final class TestConfig {
  private TestConfig() {}

  static void bootstrap() {
    SocketLayouts.load(read("sockets.yml"), read("categories.yml"));
    EnchantmentCatalog.load(read("enchantments.yml"));
  }

  private static YamlConfiguration read(String name) {
    InputStream stream = TestConfig.class.getClassLoader().getResourceAsStream(name);
    if (stream == null) throw new IllegalStateException("Missing test resource " + name);
    return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
  }
}
