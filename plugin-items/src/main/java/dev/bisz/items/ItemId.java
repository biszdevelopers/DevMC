/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.items;

import java.util.Locale;
import java.util.Objects;

public record ItemId(String namespace, String path) {
  public ItemId {
    namespace = ItemId.normalize(namespace, "namespace", "[a-z0-9_.-]+");
    path = ItemId.normalize(path, "path", "[a-z0-9/_.-]+");
  }

  public static ItemId parse(String value) {
    int separator = Objects.requireNonNull(value, "value").indexOf(58);
    if (
      separator <= 0 ||
      separator != value.lastIndexOf(58) ||
      separator == value.length() - 1
    ) {
      throw new IllegalArgumentException(
        "Item id must have namespace:path form: " + value
      );
    }
    return new ItemId(
      value.substring(0, separator),
      value.substring(separator + 1)
    );
  }

  public static ItemId of(String namespace, String path) {
    return new ItemId(namespace, path);
  }

  @Override
  public String toString() {
    return this.namespace + ":" + this.path;
  }

  private static String normalize(
    String value,
    String part,
    String expression
  ) {
    String normalized = Objects.requireNonNull(value, part).toLowerCase(
      Locale.ROOT
    );
    if (!normalized.matches(expression)) {
      throw new IllegalArgumentException("Invalid item " + part + ": " + value);
    }
    return normalized;
  }
}
