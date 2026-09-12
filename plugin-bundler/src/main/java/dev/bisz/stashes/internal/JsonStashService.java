package dev.bisz.stashes.internal;

import dev.bisz.menus.StorageProvider;
import dev.bisz.stashes.StashChangeEvent;
import dev.bisz.stashes.StashPartition;
import dev.bisz.stashes.StashService;
import dev.bisz.storage.ItemStackCodec;
import dev.bisz.storage.JsonDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** JSON-backed stash implementation. Writes are atomic through {@link JsonDatabase}. */
public final class JsonStashService implements StashService {

  private final Plugin plugin;
  private final JsonDatabase database;
  private final Map<NamespacedKey, Partition> partitions =
    new LinkedHashMap<>();

  public JsonStashService(Plugin plugin, JsonDatabase database) {
    this.plugin = plugin;
    this.database = database;
  }

  @Override
  public synchronized StashPartition registerPartition(
    Plugin owner,
    String path,
    String displayLocaleKey
  ) {
    NamespacedKey key = new NamespacedKey(owner, path);
    if (partitions.containsKey(key)) throw new IllegalArgumentException(
      "Duplicate stash partition " + key
    );
    Partition result = new Partition(
      key,
      Objects.requireNonNull(displayLocaleKey, "displayLocaleKey")
    );
    partitions.put(key, result);
    return result;
  }

  @Override
  public synchronized Optional<StashPartition> find(NamespacedKey key) {
    return Optional.ofNullable(partitions.get(key));
  }

  @Override
  public synchronized Collection<StashPartition> partitions() {
    return List.copyOf(partitions.values());
  }

  private final class Partition implements StashPartition {

    private final NamespacedKey key;
    private final String displayLocaleKey;
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> cache = new ConcurrentHashMap<>();

    private Partition(NamespacedKey key, String displayLocaleKey) {
      this.key = key;
      this.displayLocaleKey = displayLocaleKey;
    }

    @Override
    public NamespacedKey key() {
      return key;
    }

    @Override
    public String displayLocaleKey() {
      return displayLocaleKey;
    }

    private String file(UUID owner) {
      return (
        "stashes/" +
        key.getNamespace() +
        "/" +
        key.getKey() +
        "/" +
        owner +
        ".json"
      );
    }

    private Object lock(UUID owner) {
      return locks.computeIfAbsent(owner, ignored -> new Object());
    }

    @Override
    public Set<UUID> owners() {
      Path directory = database
        .root()
        .resolve("stashes")
        .resolve(key.getNamespace())
        .resolve(key.getKey());
      if (!Files.isDirectory(directory)) return Set.of();
      LinkedHashSet<UUID> result = new LinkedHashSet<>();
      try (var paths = Files.list(directory)) {
        paths
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .forEach(path -> {
            try {
              result.add(
                UUID.fromString(
                  path.getFileName().toString().replace(".json", "")
                )
              );
            } catch (IllegalArgumentException ignored) {}
          });
      } catch (java.io.IOException exception) {
        throw new IllegalStateException("Cannot list stash owners", exception);
      }
      return Set.copyOf(result);
    }

    @Override
    public List<ItemStack> items(UUID owner) {
      synchronized (lock(owner)) {
        return cached(owner)
          .stream()
          .filter(Objects::nonNull)
          .map(ItemStack::clone)
          .toList();
      }
    }

    @Override
    public boolean isEmpty(UUID owner) {
      return items(owner).isEmpty();
    }

    @Override
    public void addAll(UUID owner, Collection<ItemStack> additions) {
      synchronized (lock(owner)) {
        List<ItemStack> values = cloneItems(cached(owner));
        for (ItemStack source : additions) {
          if (
            source == null ||
            source.getType().isAir() ||
            source.getAmount() <= 0
          ) continue;
          ItemStack remaining = source.clone();
          for (ItemStack stored : values) {
            if (
              stored == null ||
              !stored.isSimilar(remaining) ||
              stored.getAmount() >= stored.getMaxStackSize()
            ) continue;
            int moved = Math.min(
              remaining.getAmount(),
              stored.getMaxStackSize() - stored.getAmount()
            );
            stored.setAmount(stored.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() == 0) break;
          }
          while (remaining.getAmount() > 0) {
            int moved = Math.min(
              remaining.getAmount(),
              remaining.getMaxStackSize()
            );
            ItemStack part = remaining.clone();
            part.setAmount(moved);
            values.add(part);
            remaining.setAmount(remaining.getAmount() - moved);
          }
        }
        save(owner, values);
        cache.put(owner, values);
        changed(owner);
      }
    }

    @Override
    public StorageProvider storage(UUID owner, int minimumSize) {
      if (minimumSize < 0) throw new IllegalArgumentException(
        "minimumSize cannot be negative"
      );
      return new StorageProvider() {
        @Override
        public int size() {
          synchronized (lock(owner)) {
            return Math.max(minimumSize, roundPage(cached(owner).size()));
          }
        }

        @Override
        public ItemStack getItem(int index) {
          synchronized (lock(owner)) {
            List<ItemStack> list = cached(owner);
            return index < list.size() && list.get(index) != null
              ? list.get(index).clone()
              : null;
          }
        }

        @Override
        public void setItem(int index, ItemStack item) {
          synchronized (lock(owner)) {
            List<ItemStack> list = cloneItems(cached(owner));
            while (list.size() <= index) list.add(null);
            list.set(index, item == null ? null : item.clone());
            trim(list);
            save(owner, list);
            cache.put(owner, list);
            changed(owner);
          }
        }
      };
    }

    private int roundPage(int size) {
      return Math.max(
        27,
        ((size + 26) / 27) * 27 + (size > 0 && size % 27 == 0 ? 27 : 0)
      );
    }

    private List<ItemStack> cached(UUID owner) {
      return cache.computeIfAbsent(owner, this::loadFromDisk);
    }

    private List<ItemStack> loadFromDisk(UUID owner) {
      Map<String, Object> root = database.loadDataFromDataBase(file(owner));
      Object raw = root.get("items");
      List<ItemStack> result = new ArrayList<>();
      if (raw instanceof List<?> list) for (Object value : list)
        result.add(
          value == null ? null : ItemStackCodec.decode(String.valueOf(value))
        );
      return result;
    }

    private List<ItemStack> cloneItems(List<ItemStack> source) {
      List<ItemStack> result = new ArrayList<>(source.size());
      for (ItemStack item : source)
        result.add(item == null ? null : item.clone());
      return result;
    }

    private void save(UUID owner, List<ItemStack> values) {
      List<String> encoded = new ArrayList<>();
      for (ItemStack value : values)
        encoded.add(value == null ? null : ItemStackCodec.encode(value));
      database.saveDataFromDataBase(
        file(owner),
        Map.of("schema", 1, "items", encoded)
      );
    }

    private void trim(List<ItemStack> values) {
      while (
        !values.isEmpty() && values.get(values.size() - 1) == null
      ) values.remove(values.size() - 1);
    }

    private void changed(UUID owner) {
      if (Bukkit.isPrimaryThread()) Bukkit.getPluginManager().callEvent(
        new StashChangeEvent(key.toString(), owner)
      );
    }
  }
}
