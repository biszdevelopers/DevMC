package dev.bisz.menus;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.entity.Player;

/** Base class shared by Bundler's concrete single-page and paged template types. */
public abstract class MenuTemplate {

  private final Function<Player, String> title;
  private final int rows;
  private final Map<Integer, MenuItem> items;
  private final Map<Integer, StorageSlot> storageSlots;
  private final Consumer<MenuOpenContext> openAction;
  private final Consumer<MenuClickContext> unhandledClickAction;
  private final Consumer<MenuCloseContext> closeAction;
  private final Consumer<MenuPageChangeContext> pageChangeAction;
  private final Consumer<MenuStorageChangeContext> storageChangeAction;

  MenuTemplate(BuilderBase<?> builder) {
    this.title = builder.title;
    this.rows = builder.rows;
    this.items = Map.copyOf(builder.items);
    this.storageSlots = Map.copyOf(builder.storageSlots);
    this.openAction = builder.openAction;
    this.unhandledClickAction = builder.unhandledClickAction;
    this.closeAction = builder.closeAction;
    this.pageChangeAction = builder.pageChangeAction;
    this.storageChangeAction = builder.storageChangeAction;
  }

  /** Returns the number of chest rows, from one through six. */
  public final int rows() {
    return rows;
  }

  /** Resolves the title for a viewer. */
  public final String title(Player player) {
    String value = Objects.requireNonNull(title.apply(player), "menu title");
    if (value.isBlank()) throw new IllegalStateException(
      "A rendered menu title cannot be blank"
    );
    return value;
  }

  final Map<Integer, MenuItem> baseItems() {
    return items;
  }

  final Map<Integer, StorageSlot> storageSlots() {
    return storageSlots;
  }

  final Consumer<MenuOpenContext> openAction() {
    return openAction;
  }

  final Consumer<MenuClickContext> unhandledClickAction() {
    return unhandledClickAction;
  }

  final Consumer<MenuCloseContext> closeAction() {
    return closeAction;
  }

  final Consumer<MenuPageChangeContext> pageChangeAction() {
    return pageChangeAction;
  }

  final Consumer<MenuStorageChangeContext> storageChangeAction() {
    return storageChangeAction;
  }

  abstract RenderedMenuPage render(
    Player player,
    int requestedPage,
    StorageProvider storage
  );

  /** Returns whether opening this template requires storage. */
  boolean requiresStorage() {
    return !storageSlots.isEmpty();
  }

  /** Resolves builder-bound storage once for a player, or {@code null}. */
  StorageProvider boundStorage(Player player) {
    return null;
  }

  /** Shared fluent implementation used by the two concrete template builders. */
  public abstract static class BuilderBase<B extends BuilderBase<B>> {

    private Function<Player, String> title;
    private final int rows;
    protected final Map<Integer, MenuItem> items = new LinkedHashMap<>();
    protected final Map<Integer, StorageSlot> storageSlots =
      new LinkedHashMap<>();
    private Consumer<MenuOpenContext> openAction;
    private Consumer<MenuClickContext> unhandledClickAction;
    private Consumer<MenuCloseContext> closeAction;
    private Consumer<MenuPageChangeContext> pageChangeAction;
    private Consumer<MenuStorageChangeContext> storageChangeAction;

    BuilderBase(String title, int rows) {
      this(ignored -> Objects.requireNonNull(title, "title"), rows);
    }

    BuilderBase(Function<Player, String> title, int rows) {
      if (rows < 1 || rows > 6) throw new IllegalArgumentException(
        "Menu rows must be 1 through 6"
      );
      this.title = Objects.requireNonNull(title, "title");
      this.rows = rows;
    }

    protected abstract B self();

    /** Uses a player-specific title. */
    public final B title(Function<Player, String> value) {
      this.title = Objects.requireNonNull(value, "value");
      return self();
    }

    /** Places or replaces a static item. */
    public final B item(int slot, MenuItem item) {
      validateSlot(slot);
      Objects.requireNonNull(item, "item");
      if (!storageSlots.containsKey(slot)) items.put(slot, item);
      return self();
    }

    /** Removes a previously configured static item. */
    public final B removeItem(int slot) {
      validateSlot(slot);
      items.remove(slot);
      return self();
    }

    /** Adds a fixed storage mapping for builders that explicitly expose it. */
    protected final B mapStorageIndex(
      int menuSlot,
      int storageSlot,
      StorageAccess access
    ) {
      validateSlot(menuSlot);
      if (storageSlot < 0) throw new IllegalArgumentException(
        "storageSlot cannot be negative"
      );
      // Storage is authoritative: a mapping replaces an earlier decorative item.
      items.remove(menuSlot);
      if (
        storageSlots.putIfAbsent(
          menuSlot,
          new StorageSlot(
            menuSlot,
            storageSlot,
            Objects.requireNonNull(access, "access")
          )
        ) !=
        null
      ) {
        throw new IllegalArgumentException(
          "Duplicate storage mapping at menu slot " + menuSlot
        );
      }
      return self();
    }

    /** Runs after the inventory is shown. */
    public final B onOpen(Consumer<MenuOpenContext> action) {
      this.openAction = Objects.requireNonNull(action, "action");
      return self();
    }

    /** Handles cancelled top-inventory clicks without a slot-specific action. */
    public final B onUnhandledClick(Consumer<MenuClickContext> action) {
      this.unhandledClickAction = Objects.requireNonNull(action, "action");
      return self();
    }

    /** Runs exactly once when the session closes. */
    public final B onClose(Consumer<MenuCloseContext> action) {
      this.closeAction = Objects.requireNonNull(action, "action");
      return self();
    }

    /** Runs after a successful page transition. */
    public final B onPageChange(Consumer<MenuPageChangeContext> action) {
      this.pageChangeAction = Objects.requireNonNull(action, "action");
      return self();
    }

    /** Runs after a mapped storage-provider index changes. */
    public final B onStorageChange(Consumer<MenuStorageChangeContext> action) {
      this.storageChangeAction = Objects.requireNonNull(action, "action");
      return self();
    }

    final void validateSlot(int slot) {
      if (slot < 0 || slot >= rows * 9) {
        throw new IllegalArgumentException(
          "Slot " + slot + " is outside a " + rows + "-row menu"
        );
      }
    }

    final void validateStorageMappings() {
      HashSet<Integer> backingSlots = new HashSet<>();
      for (StorageSlot mapping : storageSlots.values()) {
        if (!backingSlots.add(mapping.storageSlot())) {
          throw new IllegalStateException(
            "Storage-provider index " +
            mapping.storageSlot() +
            " is mapped more than once"
          );
        }
      }
    }
  }
}
