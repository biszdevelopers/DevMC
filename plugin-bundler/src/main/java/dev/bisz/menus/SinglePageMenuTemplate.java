package dev.bisz.menus;

import java.util.LinkedHashMap;
import java.util.function.Function;
import org.bukkit.entity.Player;

/** Immutable one-page chest menu template. */
public final class SinglePageMenuTemplate extends MenuTemplate {

  private SinglePageMenuTemplate(Builder builder) {
    super(builder);
  }

  /** Starts a single-page menu with a literal title. */
  public static Builder builder(String title, int rows) {
    return new Builder(title, rows);
  }

  /** Starts a single-page menu with a viewer-specific title. */
  public static Builder builder(Function<Player, String> title, int rows) {
    return new Builder(title, rows);
  }

  @Override
  RenderedMenuPage render(
    Player player,
    int requestedPage,
    StorageProvider storage
  ) {
    return new RenderedMenuPage(
      1,
      1,
      new LinkedHashMap<>(baseItems()),
      storageSlots(),
      null,
      null
    );
  }

  /** Fluent single-page template builder. */
  public static final class Builder extends MenuTemplate.BuilderBase<Builder> {

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

    /** Maps a writable GUI cell to an exact provider index. */
    public Builder storageIndex(int menuSlot, int storageIndex) {
      return mapStorageIndex(menuSlot, storageIndex, StorageAccess.READ_WRITE);
    }

    /** Compatibility alias for {@link #storageIndex(int, int)}. */
    public Builder storageSlot(int menuSlot, int storageIndex) {
      return storageIndex(menuSlot, storageIndex);
    }

    /** Maps a read-only GUI cell to an exact provider index. */
    public Builder readOnlyStorageIndex(int menuSlot, int storageIndex) {
      return mapStorageIndex(menuSlot, storageIndex, StorageAccess.VIEW_ONLY);
    }

    /** Maps a cell with an explicit transfer policy. */
    public Builder storageIndex(
      int menuSlot,
      int storageIndex,
      StorageAccess access
    ) {
      return mapStorageIndex(menuSlot, storageIndex, access);
    }

    /** Compatibility alias for {@link #readOnlyStorageIndex(int, int)}. */
    public Builder readOnlyStorageSlot(int menuSlot, int storageIndex) {
      return readOnlyStorageIndex(menuSlot, storageIndex);
    }

    /** Builds the validated immutable template. */
    public SinglePageMenuTemplate build() {
      validateStorageMappings();
      return new SinglePageMenuTemplate(this);
    }
  }
}
