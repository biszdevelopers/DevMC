/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.items;

import dev.bisz.items.ItemDataType;
import java.util.Objects;

public record ItemMetadata(String key, ItemDataType type, Object defaultValue) {
  public ItemMetadata {
    if (!Objects.requireNonNull(key, "key").matches("[a-z0-9/._-]{1,128}")) {
      throw new IllegalArgumentException("Invalid metadata key: " + key);
    }
    Objects.requireNonNull(type, "type");
    if (!ItemMetadata.matches(type, defaultValue)) {
      throw new IllegalArgumentException(
        "Default value does not match " + String.valueOf((Object) type)
      );
    }
  }

  public static boolean matches(ItemDataType type, Object value) {
    return switch (type) {
      default -> throw new IncompatibleClassChangeError();
      case STRING -> value instanceof String;
      case INTEGER -> value instanceof Integer;
      case LONG -> value instanceof Long;
      case DOUBLE -> value instanceof Double;
      case BOOLEAN -> value instanceof Boolean;
    };
  }
}
