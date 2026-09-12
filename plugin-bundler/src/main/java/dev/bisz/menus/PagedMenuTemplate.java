package dev.bisz.menus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.bukkit.entity.Player;

/** Automatic list pagination with a shared base layout and static page overrides. */
public final class PagedMenuTemplate<T> extends MenuTemplate {

  private final Function<Player, List<T>> entries;
  private final BiFunction<Player, T, MenuItem> renderer;
  private final List<Integer> contentSlots;
  private final Integer previousSlot;
  private final MenuItem previousItem;
  private final Integer nextSlot;
  private final MenuItem nextItem;
  private final Map<Integer, PageOverride> pageOverrides;

  private PagedMenuTemplate(Builder<T> builder) {
    super(builder);
    this.entries = builder.entries;
    this.renderer = builder.renderer;
    this.contentSlots = List.copyOf(builder.contentSlots);
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

  /** Starts a paged menu with a literal title. */
  public static <T> Builder<T> builder(String title, int rows) {
    return new Builder<>(title, rows);
  }

  /** Starts a paged menu with a viewer-specific title. */
  public static <T> Builder<T> builder(
    Function<Player, String> title,
    int rows
  ) {
    return new Builder<>(title, rows);
  }

  @Override
  RenderedMenuPage render(
    Player player,
    int requestedPage,
    StorageProvider storage
  ) {
    List<T> supplied = List.copyOf(
      Objects.requireNonNull(entries.apply(player), "paged entries")
    );
    int pageCount = Math.max(
      1,
      (supplied.size() + contentSlots.size() - 1) / contentSlots.size()
    );
    int page = Math.max(1, Math.min(requestedPage, pageCount));
    LinkedHashMap<Integer, MenuItem> result = new LinkedHashMap<>(baseItems());
    PageOverride override = pageOverrides.get(page);
    if (override != null) {
      override.removed.forEach(result::remove);
      result.putAll(override.items);
    }
    int offset = (page - 1) * contentSlots.size();
    for (
      int index = 0;
      index < contentSlots.size() && offset + index < supplied.size();
      index++
    ) {
      MenuItem item = Objects.requireNonNull(
        renderer.apply(player, supplied.get(offset + index)),
        "rendered page item"
      );
      result.put(contentSlots.get(index), item);
    }
    Integer shownPrevious = page > 1 ? previousSlot : null;
    Integer shownNext = page < pageCount ? nextSlot : null;
    if (shownPrevious != null) result.put(shownPrevious, previousItem);
    else if (previousSlot != null) result.remove(previousSlot);
    if (shownNext != null) result.put(shownNext, nextItem);
    else if (nextSlot != null) result.remove(nextSlot);
    return new RenderedMenuPage(
      page,
      pageCount,
      result,
      Map.of(),
      shownPrevious,
      shownNext
    );
  }

  /** Fluent paged-template builder. */
  public static final class Builder<T>
    extends MenuTemplate.BuilderBase<Builder<T>> {

    private Function<Player, List<T>> entries = ignored -> List.of();
    private BiFunction<Player, T, MenuItem> renderer;
    private final List<Integer> contentSlots = new ArrayList<>();
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
    protected Builder<T> self() {
      return this;
    }

    /** Uses a defensive snapshot as page content. */
    public Builder<T> entries(Collection<T> values) {
      List<T> copied = List.copyOf(values);
      this.entries = ignored -> copied;
      return this;
    }

    /** Calculates the current content list for each viewer and refresh. */
    public Builder<T> entries(Function<Player, List<T>> values) {
      this.entries = Objects.requireNonNull(values, "values");
      return this;
    }

    /** Converts each content value into a rendered menu item. */
    public Builder<T> renderItem(BiFunction<Player, T, MenuItem> value) {
      this.renderer = Objects.requireNonNull(value, "value");
      return this;
    }

    /** Defines ordered page-content cells. */
    public Builder<T> contentSlots(int... slots) {
      contentSlots.clear();
      HashSet<Integer> unique = new HashSet<>();
      for (int slot : slots) {
        validateSlot(slot);
        if (!unique.add(slot)) throw new IllegalArgumentException(
          "Duplicate content slot " + slot
        );
        contentSlots.add(slot);
      }
      return this;
    }

    /** Places the previous-page control. */
    public Builder<T> previousButton(int slot, MenuItem item) {
      validateSlot(slot);
      this.previousSlot = slot;
      this.previousItem = Objects.requireNonNull(item, "item");
      return this;
    }

    /** Places the next-page control. */
    public Builder<T> nextButton(int slot, MenuItem item) {
      validateSlot(slot);
      this.nextSlot = slot;
      this.nextItem = Objects.requireNonNull(item, "item");
      return this;
    }

    /** Adds or replaces one static item on a one-based page. */
    public Builder<T> pageItem(int page, int slot, MenuItem item) {
      validatePageSlot(page, slot);
      PageOverride override = pageOverrides.computeIfAbsent(page, ignored ->
        new PageOverride()
      );
      override.removed.remove(slot);
      override.items.put(slot, Objects.requireNonNull(item, "item"));
      return this;
    }

    /** Removes an inherited static item from a one-based page. */
    public Builder<T> removePageItem(int page, int slot) {
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
    public PagedMenuTemplate<T> build() {
      if (contentSlots.isEmpty()) throw new IllegalStateException(
        "A paged menu needs at least one content slot"
      );
      if (renderer == null) throw new IllegalStateException(
        "A paged menu needs an item renderer"
      );
      HashSet<Integer> reserved = new HashSet<>(contentSlots);
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
      // Paged content and navigation controls are authoritative over decoration.
      items.keySet().removeIf(reserved::contains);
      for (Map.Entry<Integer, PageOverride> entry : pageOverrides.entrySet()) {
        entry.getValue().items.keySet().removeIf(reserved::contains);
        entry.getValue().removed.removeIf(reserved::contains);
      }
      return new PagedMenuTemplate<>(this);
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
