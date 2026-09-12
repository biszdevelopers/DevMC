package dev.bisz.items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.NamespacedKey;

/** Immutable level and per-item metadata for one applied enchantment. */
public record EnchantmentData(int level, Map<NamespacedKey, Object> metadata) {

  public EnchantmentData {
    if (level < 1 || level > 3999) throw new IllegalArgumentException(
      "Enchantment levels must be 1..3999"
    );
    LinkedHashMap<NamespacedKey, Object> copied = new LinkedHashMap<>();
    Objects.requireNonNull(metadata, "metadata").forEach((key, value) -> {
      Objects.requireNonNull(key, "metadata key");
      if (!(value instanceof Boolean) && !(value instanceof Integer)) {
        throw new IllegalArgumentException(
          "Enchantment metadata values must be Boolean or Integer: " + key
        );
      }
      copied.put(key, value);
    });
    metadata = Map.copyOf(copied);
  }

  public EnchantmentData(int level) {
    this(level, Map.of());
  }
}
