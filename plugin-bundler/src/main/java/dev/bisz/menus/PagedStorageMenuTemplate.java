package dev.bisz.menus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.entity.Player;

/** Automatic pagination over consecutive excerpts of a {@link StorageProvider}. */
public final class PagedStorageMenuTemplate extends MenuTemplate {

  private final Function<Player, StorageProvider> storage;
  private final List<Integer> viewportSlots;
  private final Map<Integer, StorageAccess> accessSlots;
  private final Integer previousSlot;
  private final MenuItem previousItem;
  private final Integer nextSlot;
  private final MenuItem nextItem;
  private final Map<Integer, PageOverride> pageOverrides;

  private PagedStorageMenuTemplate(Builder builder) {
    super(builder);
    this.storage = builder.storage;
    this.viewportSlots = List.copyOf(builder.viewportSlots);
    this.accessSlots = Map.copyOf(builder.accessSlots);
    this.previousSlot = builder.previousSlot;
    this.previousItem = builder.previousItem;
    this.nextSlot = builder.nextSlot;
    this.nextItem = builder.nextItem;
    LinkedHashMap<Integer, PageOverride> overrides = new LinkedHashMap<>();
    builder.pageOverrides.forEach((page, value) ->
      overrides.put(page, value.copy())
    );
    this.pageOverrides = Map.copyOf(overrides);
  }

  /** Starts a paged storage menu with a literal title. */
  public static Builder builder(String title, int rows) {
    return new Builder(title, rows);
  }

  /** Starts a paged storage menu with a viewer-specific title. */
  public static Builder builder(Function<Player, String> title, int rows) {
    return new Builder(title, rows);
  }

  @Override
  boolean requiresStorage() {
    return true;
  }

  @Override
  StorageProvider boundStorage(Player player) {
    return Objects.requireNonNull(
      storage.apply(player),
      "paged storage provider"
    );
  }

  @Override
  RenderedMenuPage render(
    Player player,
    int requestedPage,
    StorageProvider provider
  ) {
    Objects.requireNonNull(provider, "paged storage provider");
    int size = provider.size();
    if (size < 0) throw new IllegalStateException(
      "StorageProvider size cannot be negative"
    );
    int pageCount = Math.max(
      1,
      (int) ((size + (long) viewportSlots.size() - 1L) / viewportSlots.size())
    );
    int page = Math.max(1, Math.min(requestedPage, pageCount));
    LinkedHashMap<Integer, MenuItem> items = new LinkedHashMap<>(baseItems());
    PageOverride override = pageOverrides.get(page);
    if (override != null) {
      override.removed.forEach(items::remove);
      items.putAll(override.items);
    }
    LinkedHashMap<Integer, StorageSlot> mappings = new LinkedHashMap<>();
    int offset = (page - 1) * viewportSlots.size();
    for (
      int order = 0;
      order < viewportSlots.size() && offset + order < size;
      order++
    ) {
      int menuSlot = viewportSlots.get(order);
      mappings.put(
        menuSlot,
        new StorageSlot(
          menuSlot,
          offset + order,
          accessSlots.getOrDefault(menuSlot, StorageAccess.READ_WRITE)
        )
      );
    }
    Integer shownPrevious = page > 1 ? previousSlot : null;
    Integer shownNext = page < pageCount ? nextSlot : null;
    if (shownPrevious != null) items.put(shownPrevious, previousItem);
    if (shownNext != null) items.put(shownNext, nextItem);
    return new RenderedMenuPage(
      page,
      pageCount,
      items,
      mappings,
      shownPrevious,
      shownNext
    );
  }

  /** Fluent paged-storage builder. */
  public static final class Builder extends MenuTemplate.BuilderBase<Builder> {

    private Function<Player, StorageProvider> storage;
    private final List<Integer> viewportSlots = new ArrayList<>();
    private final Map<Integer, StorageAccess> accessSlots =
      new LinkedHashMap<>();
    private Integer previousSlot;
    private MenuItem previousItem;
    private Integer nextSlot;
    private MenuItem nextItem;
    private final Map<Integer, PageOverride> pageOverrides =
      new LinkedHashMap<>();

    private Builder(String title, int rows) {
      super(title, rows);
    }

    private Builder(Function<Player, String> title, int rows) {
      super(title, rows);
    }

    @Override
    protected Builder self() {
      return this;
    }

    /** Binds one provider to every session opened from this template. */
    public Builder storage(StorageProvider value) {
      Objects.requireNonNull(value, "value");
      this.storage = ignored -> value;
      return this;
    }

    /** Resolves and binds one provider when each player opens the menu. */
    public Builder storage(Function<Player, StorageProvider> value) {
      this.storage = Objects.requireNonNull(value, "value");
      return this;
    }

    /** Defines the ordered GUI viewport used for each provider excerpt. */
    public Builder storageSlots(int... slots) {
      viewportSlots.clear();
      HashSet<Integer> unique = new HashSet<>();
      for (int slot : slots) {
        validateSlot(slot);
        if (!unique.add(slot)) throw new IllegalArgumentException(
          "Duplicate storage viewport slot " + slot
        );
        viewportSlots.add(slot);
      }
      return this;
    }

    /** Marks selected declared viewport cells as read-only. */
    public Builder readOnlySlots(int... slots) {
      accessSlots.clear();
      for (int slot : slots) {
        validateSlot(slot);
        if (
          accessSlots.putIfAbsent(slot, StorageAccess.VIEW_ONLY) != null
        ) throw new IllegalArgumentException(
          "Duplicate read-only slot " + slot
        );
      }
      return this;
    }

    /** Assigns one transfer policy to selected viewport cells. */
    public Builder access(StorageAccess access, int... slots) {
      Objects.requireNonNull(access, "access");
      for (int slot : slots) {
        validateSlot(slot);
        if (!viewportSlots.contains(slot)) throw new IllegalArgumentException(
          "Access slot is not in viewport: " + slot
        );
        accessSlots.put(slot, access);
      }
      return this;
    }

    /** Places the previous-page control. */
    public Builder previousButton(int slot, MenuItem item) {
      validateSlot(slot);
      this.previousSlot = slot;
      this.previousItem = Objects.requireNonNull(item, "item");
      return this;
    }

    /** Places the next-page control. */
    public Builder nextButton(int slot, MenuItem item) {
      validateSlot(slot);
      this.nextSlot = slot;
      this.nextItem = Objects.requireNonNull(item, "item");
      return this;
    }

    /** Adds or replaces one static item on a one-based page. */
    public Builder pageItem(int page, int slot, MenuItem item) {
      validatePageSlot(page, slot);
      PageOverride override = pageOverrides.computeIfAbsent(page, ignored ->
        new PageOverride()
      );
      override.removed.remove(slot);
      override.items.put(slot, Objects.requireNonNull(item, "item"));
      return this;
    }

    /** Removes an inherited static item from a one-based page. */
    public Builder removePageItem(int page, int slot) {
      validatePageSlot(page, slot);
      PageOverride override = pageOverrides.computeIfAbsent(page, ignored ->
        new PageOverride()
      );
      override.items.remove(slot);
      override.removed.add(slot);
      return this;
    }

    private void validatePageSlot(int page, int slot) {
      if (page < 1) throw new IllegalArgumentException(
        "page must be one or greater"
      );
      validateSlot(slot);
    }

    /** Builds the validated immutable template. */
    public PagedStorageMenuTemplate build() {
      if (storage == null) throw new IllegalStateException(
        "A paged storage menu needs a StorageProvider"
      );
      if (viewportSlots.isEmpty()) throw new IllegalStateException(
        "A paged storage menu needs at least one storage slot"
      );
      if (!viewportSlots.containsAll(accessSlots.keySet())) {
        throw new IllegalStateException(
          "Every read-only slot must be declared by storageSlots"
        );
      }
      HashSet<Integer> reserved = new HashSet<>(viewportSlots);
      if (
        previousSlot != null && !reserved.add(previousSlot)
      ) throw new IllegalStateException(
        "Previous button conflicts with another slot"
      );
      if (
        nextSlot != null && !reserved.add(nextSlot)
      ) throw new IllegalStateException(
        "Next button conflicts with another slot"
      );
      // Storage is authoritative over decoration. Navigation controls temporarily
      // replace decoration while available, leaving it as a static fallback.
      items.keySet().removeIf(viewportSlots::contains);
      for (Map.Entry<Integer, PageOverride> entry : pageOverrides.entrySet()) {
        entry.getValue().items.keySet().removeIf(viewportSlots::contains);
        entry.getValue().removed.removeIf(viewportSlots::contains);
      }
      return new PagedStorageMenuTemplate(this);
    }
  }

  private static final class PageOverride {

    private final Map<Integer, MenuItem> items = new LinkedHashMap<>();
    private final Set<Integer> removed = new HashSet<>();

    private PageOverride copy() {
      PageOverride copy = new PageOverride();
      copy.items.putAll(items);
      copy.removed.addAll(removed);
      return copy;
    }

    private Set<Integer> allSlots() {
      HashSet<Integer> slots = new HashSet<>(items.keySet());
      slots.addAll(removed);
      return slots;
    }
  }
}
