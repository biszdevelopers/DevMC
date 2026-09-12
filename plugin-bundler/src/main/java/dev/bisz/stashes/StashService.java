package dev.bisz.stashes;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Registry of isolated persistent item-stash partitions. */
public interface StashService {
  StashPartition registerPartition(
    Plugin owner,
    String path,
    String displayLocaleKey
  );
  Optional<StashPartition> find(NamespacedKey key);
  Collection<StashPartition> partitions();
}
