package dev.bisz.items;

import java.util.Locale;
import java.util.Objects;

/** Namespaced identifier for a DevEnchantment. */
public record EnchantmentId(String namespace, String path) {
  public EnchantmentId {
    namespace = normalize(namespace, "namespace", "[a-z0-9_.-]+");
    path = normalize(path, "path", "[a-z0-9/_.-]+");
  }

  public static EnchantmentId of(String namespace, String path) {
    return new EnchantmentId(namespace, path);
  }

  public static EnchantmentId parse(String value) {
    int separator = Objects.requireNonNull(value, "value").indexOf(':');
    if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
      throw new IllegalArgumentException("Enchantment id must have namespace:path form: " + value);
    }
    return new EnchantmentId(value.substring(0, separator), value.substring(separator + 1));
  }

  @Override public String toString() { return namespace + ":" + path; }

  private static String normalize(String value, String part, String expression) {
    String normalized = Objects.requireNonNull(value, part).toLowerCase(Locale.ROOT);
    if (!normalized.matches(expression)) throw new IllegalArgumentException("Invalid enchantment " + part + ": " + value);
    return normalized;
  }
}
