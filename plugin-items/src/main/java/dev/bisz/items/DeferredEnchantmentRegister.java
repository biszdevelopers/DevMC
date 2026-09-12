package dev.bisz.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

/** Deferred custom-enchantment registration helper, parallel to DeferredItemRegister. */
public final class DeferredEnchantmentRegister {
  private final Plugin owner;
  private final String namespace;
  private final List<Entry<?>> entries = new ArrayList<>();
  private boolean applied;
  private DeferredEnchantmentRegister(Plugin owner, String namespace) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.namespace = EnchantmentId.of(namespace, "placeholder").namespace();
  }
  public static DeferredEnchantmentRegister create(Plugin owner, String namespace) { return new DeferredEnchantmentRegister(owner, namespace); }
  public <T extends CustomEnchantment> EnchantmentRegistryObject<T> register(String path, Supplier<T> supplier) {
    if (applied) throw new IllegalStateException("Register already applied");
    EnchantmentId id = EnchantmentId.of(namespace, path);
    EnchantmentRegistryObject<T> handle = new EnchantmentRegistryObject<>(id);
    entries.add(new Entry<>(id, supplier, handle));
    return handle;
  }
  public void apply(EnchantmentRegistry registry) {
    if (applied) throw new IllegalStateException("Register already applied");
    List<CustomEnchantment> created = new ArrayList<>();
    entries.forEach(entry -> created.add(entry.create()));
    registry.registerAll(owner, created);
    entries.forEach(Entry::resolve);
    applied = true;
  }
  private static final class Entry<T extends CustomEnchantment> {
    private final EnchantmentId id; private final Supplier<T> supplier; private final EnchantmentRegistryObject<T> handle; private T created;
    Entry(EnchantmentId id, Supplier<T> supplier, EnchantmentRegistryObject<T> handle) { this.id=id; this.supplier=Objects.requireNonNull(supplier,"supplier"); this.handle=handle; }
    T create() { created=Objects.requireNonNull(supplier.get(),"enchantment supplier returned null"); if (!created.id().equals(id)) throw new IllegalArgumentException("Registered enchantment id does not match request: " + id + " != " + created.id()); return created; }
    void resolve() { handle.resolve(created); }
  }
}
