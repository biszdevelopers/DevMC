/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.items;

import dev.bisz.items.DevItem;
import dev.bisz.items.ItemId;
import java.util.Objects;
import java.util.function.Supplier;

public final class RegistryObject<T extends DevItem> implements Supplier<T> {

  private final ItemId id;
  private T value;

  RegistryObject(ItemId id) {
    this.id = Objects.requireNonNull(id, "id");
  }

  public ItemId id() {
    return this.id;
  }

  @Override
  public T get() {
    if (this.value == null) {
      throw new IllegalStateException(
        "Item has not been registered: " + String.valueOf(this.id)
      );
    }
    return this.value;
  }

  void resolve(T registered) {
    if (this.value != null) {
      throw new IllegalStateException(
        "Item already resolved: " + String.valueOf(this.id)
      );
    }
    this.value = registered;
  }
}
