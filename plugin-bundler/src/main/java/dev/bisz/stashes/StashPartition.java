package dev.bisz.stashes;

import dev.bisz.menus.StorageProvider;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/** One namespaced, independently owned stash collection. */
public interface StashPartition {
  NamespacedKey key();
  String displayLocaleKey();
  Set<UUID> owners();
  List<ItemStack> items(UUID owner);
  boolean isEmpty(UUID owner);
  void addAll(UUID owner, Collection<ItemStack> items);
  StorageProvider storage(UUID owner, int minimumSize);
}
