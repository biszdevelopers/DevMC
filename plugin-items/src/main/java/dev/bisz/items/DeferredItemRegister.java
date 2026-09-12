/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.plugin.Plugin
 */
package dev.bisz.items;

import dev.bisz.items.CustomItem;
import dev.bisz.items.DevItem;
import dev.bisz.items.ItemId;
import dev.bisz.items.ItemRegistry;
import dev.bisz.items.RegistryObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

public final class DeferredItemRegister {

  private final Plugin owner;
  private final String namespace;
  private final List<Entry<?>> entries = new ArrayList();
  private boolean applied;

  private DeferredItemRegister(Plugin owner, String namespace) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.namespace = new ItemId(namespace, "placeholder").namespace();
  }

  public static DeferredItemRegister create(Plugin owner, String namespace) {
    return new DeferredItemRegister(owner, namespace);
  }

  public <T extends CustomItem> RegistryObject<T> register(
    String path,
    Supplier<T> supplier
  ) {
    if (this.applied) {
      throw new IllegalStateException("Register already applied");
    }
    ItemId id = ItemId.of(this.namespace, path);
    RegistryObject<T> handle = new RegistryObject<T>(id);
    this.entries.add(new Entry<T>(id, supplier, handle));
    return handle;
  }

  public void apply(ItemRegistry registry) {
    if (this.applied) {
      throw new IllegalStateException("Register already applied");
    }
    ArrayList<DevItem> created = new ArrayList<DevItem>();
    for (Entry<?> entry : this.entries) {
      created.add(entry.create());
    }
    registry.registerAll(this.owner, created);
    for (Entry<?> entry : this.entries) {
      entry.resolve();
    }
    this.applied = true;
  }

  private static final class Entry<T extends CustomItem> {

    private final ItemId id;
    private final Supplier<T> supplier;
    private final RegistryObject<T> handle;
    private T created;

    Entry(ItemId id, Supplier<T> supplier, RegistryObject<T> handle) {
      this.id = id;
      this.supplier = Objects.requireNonNull(supplier, "supplier");
      this.handle = handle;
    }

    DevItem create() {
      this.created = Objects.requireNonNull(
        this.supplier.get(),
        "item supplier returned null"
      );
      if (!((DevItem) this.created).id().equals(this.id)) {
        throw new IllegalArgumentException(
          "Registered item id does not match request: " +
          String.valueOf(this.id) +
          " != " +
          String.valueOf(((DevItem) this.created).id())
        );
      }
      return this.created;
    }

    void resolve() {
      this.handle.resolve(this.created);
    }
  }
}
