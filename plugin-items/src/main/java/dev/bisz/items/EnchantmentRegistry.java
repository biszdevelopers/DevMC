package dev.bisz.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.Plugin;

/** Registry for all ItemLib enchantment definitions. */
public final class EnchantmentRegistry {
  private final Map<EnchantmentId, DevEnchantment> entries = new LinkedHashMap<>();
  private final Map<Plugin, List<EnchantmentId>> owners = new IdentityHashMap<>();
  private boolean vanillaRegistered;

  public synchronized void registerVanillaEnchantments() {
    if (vanillaRegistered) throw new IllegalStateException("Vanilla enchantments are already registered");
    for (Enchantment enchantment : Enchantment.values()) {
      if (!"minecraft".equals(enchantment.getKey().getNamespace())) continue;
      registerInternal(null, new VanillaEnchantment(enchantment));
    }
    vanillaRegistered = true;
  }
  public synchronized void register(Plugin owner, CustomEnchantment enchantment) { registerAll(owner, List.of(enchantment)); }
  public synchronized void registerAll(Plugin owner, Collection<? extends CustomEnchantment> definitions) {
    Objects.requireNonNull(owner, "owner");
    List<? extends CustomEnchantment> copy = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
    HashSet<EnchantmentId> ids = new HashSet<>();
    for (CustomEnchantment enchantment : copy) {
      if (!ids.add(enchantment.id()) || entries.containsKey(enchantment.id())) throw new IllegalArgumentException("Duplicate enchantment id: " + enchantment.id());
    }
    copy.forEach(enchantment -> registerInternal(owner, enchantment));
  }
  public synchronized void unregisterAll(Plugin owner) {
    List<EnchantmentId> ids = owners.remove(owner);
    if (ids != null) ids.forEach(entries::remove);
  }
  public synchronized Optional<DevEnchantment> get(EnchantmentId id) { return Optional.ofNullable(entries.get(id)); }
  public synchronized Collection<DevEnchantment> values() { return List.copyOf(entries.values()); }
  public synchronized boolean hasTickingDefinitions() {
    return entries.values().stream().anyMatch(e -> !e.vanilla() && (e.properties().handTicking() || e.properties().inventoryTicking()));
  }
  private void registerInternal(Plugin owner, DevEnchantment enchantment) {
    if (entries.putIfAbsent(enchantment.id(), enchantment) != null) throw new IllegalArgumentException("Duplicate enchantment id: " + enchantment.id());
    if (owner != null) owners.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(enchantment.id());
  }
}
