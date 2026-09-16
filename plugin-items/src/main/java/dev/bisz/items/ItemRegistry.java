/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package dev.bisz.items;

import dev.bisz.items.CustomItem;
import dev.bisz.items.DevItem;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.ItemId;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.items.UnresolvedItem;
import dev.bisz.items.VanillaItem;
import dev.bisz.items.VanillaItemOverride;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

@SuppressWarnings("deprecation")
public final class ItemRegistry {

  private final Map<ItemId, DevItem> entries = new LinkedHashMap<
    ItemId,
    DevItem
  >();
  private final Map<Plugin, List<ItemId>> owners = new IdentityHashMap<
    Plugin,
    List<ItemId>
  >();
  private final Map<ItemId, VanillaItem> generatedVanilla =
    new LinkedHashMap<ItemId, VanillaItem>();
  private final Map<ItemId, OverrideVanillaItem> vanillaDefinitions =
    new LinkedHashMap<ItemId, OverrideVanillaItem>();
  private final Map<Plugin, List<ItemId>> vanillaOwners = new IdentityHashMap<
    Plugin,
    List<ItemId>
  >();
  private final Map<ItemId, List<VanillaItemOverride>> overrides =
    new LinkedHashMap<ItemId, List<VanillaItemOverride>>();
  private boolean vanillaRegistered;

  public synchronized void registerVanillaItems() {
    if (this.vanillaRegistered) {
      throw new IllegalStateException("Vanilla items are already registered");
    }
    HashSet<ItemId> registered = new HashSet<>();
    for (Material material : Material.values()) {
      boolean air = material == Material.AIR ||
        material == Material.CAVE_AIR ||
        material == Material.VOID_AIR;
      if (air || material.name().startsWith("LEGACY_")) continue;
      if (Bukkit.getServer() != null && !material.isItem()) continue;
      ItemId id = ItemId.of("minecraft", material.getKey().getKey());
      if (!registered.add(id)) continue; // Hybrid servers may expose aliases with one Bukkit key.
      VanillaItem item = new VanillaItem(material);
      this.generatedVanilla.put(id, item);
      this.registerInternal(null, item);
    }
    this.vanillaRegistered = true;
  }

  public synchronized void register(Plugin owner, CustomItem item) {
    this.registerAll(owner, List.of(item));
  }

  /** Replaces one generated vanilla definition until its owning plugin unregisters. */
  public synchronized void registerVanillaOverride(
    Plugin owner,
    OverrideVanillaItem override
  ) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(override, "override");
    if (!this.vanillaRegistered) {
      throw new IllegalStateException(
        "Vanilla items must be registered before vanilla overrides"
      );
    }
    ItemId id = override.id();
    VanillaItem generated = this.generatedVanilla.get(id);
    if (generated == null) {
      throw new IllegalArgumentException(
        "Not a generated vanilla item: " + String.valueOf(id)
      );
    }
    if (generated.material() != override.material()) {
      throw new IllegalArgumentException(
        "Vanilla override material does not match " + String.valueOf(id)
      );
    }
    if (this.vanillaDefinitions.containsKey(id)) {
      throw new IllegalArgumentException(
        "Duplicate vanilla override: " + String.valueOf(id)
      );
    }
    this.vanillaDefinitions.put(id, override);
    this.entries.put(id, override);
    this.vanillaOwners.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(id);
  }

  public synchronized void registerAll(
    Plugin owner,
    Collection<? extends DevItem> definitions
  ) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(definitions, "definitions");
    List<? extends DevItem> copy = List.copyOf(definitions);
    HashSet<ItemId> ids = new HashSet<ItemId>();
    for (DevItem devItem : copy) {
      if (devItem.vanilla()) {
        throw new IllegalArgumentException(
          "Plugins cannot register VanillaItem definitions"
        );
      }
      if (
        !this.entries.containsKey(devItem.id()) && ids.add(devItem.id())
      ) continue;
      throw new IllegalArgumentException(
        "Duplicate item id: " + String.valueOf(devItem.id())
      );
    }
    for (DevItem devItem : copy) {
      this.registerInternal(owner, devItem);
    }
  }

  public synchronized void unregisterAll(Plugin owner) {
    List<ItemId> ids = this.owners.remove(owner);
    if (ids != null) {
      ids.forEach(this.entries::remove);
    }
    List<ItemId> vanillaIds = this.vanillaOwners.remove(owner);
    if (vanillaIds != null) {
      for (ItemId id : vanillaIds) {
        this.vanillaDefinitions.remove(id);
        VanillaItem generated = this.generatedVanilla.get(id);
        if (generated != null) this.entries.put(id, generated);
      }
    }
  }

  public synchronized Optional<DevItem> get(ItemId id) {
    return Optional.ofNullable(this.entries.get(id));
  }

  public synchronized Collection<DevItem> values() {
    return List.copyOf(this.entries.values());
  }

  synchronized boolean hasTickingDefinitions() {
    return this.entries.values().stream().anyMatch(item ->
      item.behaviorsEnabled() &&
      (item.properties().handTicking() || item.properties().inventoryTicking())
    );
  }

  public synchronized boolean hasVanillaOverrides(ItemId id) {
    return this.vanillaDefinitions.containsKey(id) ||
      !this.overrides.getOrDefault(id, List.of()).isEmpty();
  }

  /** @deprecated Prefer a class derived from OverrideVanillaItem. */
  @Deprecated(forRemoval = false)
  public synchronized void addVanillaOverride(
    ItemId id,
    VanillaItemOverride override
  ) {
    DevItem item = this.entries.get(id);
    if (!(item instanceof VanillaItem)) {
      throw new IllegalArgumentException(
        "Not a generated vanilla item: " + String.valueOf(id)
      );
    }
    this.overrides.computeIfAbsent(id, ignored -> new ArrayList<>()).add(
      Objects.requireNonNull(override, "override")
    );
  }

  public synchronized DevItemStack resolve(ItemStack stack) {
    Objects.requireNonNull(stack, "stack");
    if (stack.getType().isAir()) {
      throw new IllegalArgumentException("Air cannot be resolved as an item");
    }
    ItemId embedded = ItemRegistry.embeddedId(stack);
    DevItem item = embedded != null
      ? this.entries.getOrDefault(
        embedded,
        new UnresolvedItem(embedded, stack.getType())
      )
      : this.entries.get(
        ItemId.of("minecraft", stack.getType().getKey().getKey())
      );
    if (item == null) {
      throw new IllegalStateException(
        "Vanilla catalog is unavailable for " + String.valueOf(stack.getType())
      );
    }
    DevItemStack resolved = new DevItemStack(item, stack);
    item.load(resolved);
    if (item instanceof VanillaItem) {
      this.overrides.getOrDefault(item.id(), List.of()).forEach(override ->
        override.onLoaded(resolved)
      );
    }
    return resolved;
  }

  public synchronized DevItemStack create(ItemId id, int amount) {
    DevItem item = this.entries.get(id);
    if (item == null) {
      throw new IllegalArgumentException("Unknown item: " + String.valueOf(id));
    }
    DevItemStack created = item.createStack(amount);
    if (item instanceof VanillaItem) {
      this.overrides.getOrDefault(item.id(), List.of()).forEach(override ->
        override.onLoaded(created)
      );
    }
    return created;
  }

  private void registerInternal(Plugin owner, DevItem item) {
    if (this.entries.putIfAbsent(item.id(), item) != null) {
      throw new IllegalArgumentException(
        "Duplicate item id: " + String.valueOf(item.id())
      );
    }
    if (owner != null) {
      this.owners.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(
        item.id()
      );
    }
  }

  private static ItemId embeddedId(ItemStack stack) {
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return null;
    }
    String value = (String) meta
      .getPersistentDataContainer()
      .get(
        new NamespacedKey((Plugin) ItemsPlugin.instance(), "item-id"),
        PersistentDataType.STRING
      );
    return value == null ? null : ItemId.parse(value);
  }
}
