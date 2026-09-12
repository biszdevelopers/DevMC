/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package dev.bisz.items;

import dev.bisz.items.ItemDataType;
import dev.bisz.items.ItemMetadata;
import dev.bisz.items.Quality;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;

public final class ItemProperties {

  private final Material material;
  private final int maximumStackSize;
  private final Quality quality;
  private final boolean tradeable;
  private final boolean placeable;
  private final String category;
  private final boolean handTicking;
  private final boolean inventoryTicking;
  private final Map<String, ItemMetadata> metadata;

  private ItemProperties(Builder builder) {
    this.material = builder.material;
    this.maximumStackSize = builder.maximumStackSize;
    this.quality = builder.quality;
    this.tradeable = builder.tradeable;
    this.placeable = builder.placeable;
    this.category = builder.category;
    this.handTicking = builder.handTicking;
    this.inventoryTicking = builder.inventoryTicking;
    this.metadata = Map.copyOf(builder.metadata);
  }

  public static Builder builder(Material material) {
    return new Builder(material);
  }

  public Material material() {
    return this.material;
  }

  public int maximumStackSize() {
    return this.maximumStackSize;
  }

  public Quality quality() {
    return this.quality;
  }

  public boolean tradeable() {
    return this.tradeable;
  }

  public boolean placeable() {
    return this.placeable;
  }

  public String category() {
    return this.category;
  }

  public boolean handTicking() { return this.handTicking; }

  public boolean inventoryTicking() { return this.inventoryTicking; }

  public Map<String, ItemMetadata> metadata() {
    return this.metadata;
  }

  public static final class Builder {

    private final Material material;
    private int maximumStackSize;
    private Quality quality = Quality.COMMON;
    private boolean tradeable = true;
    private boolean placeable = true;
    private String category = "";
    private boolean handTicking;
    private boolean inventoryTicking;
    private final Map<String, ItemMetadata> metadata = new LinkedHashMap<
      String,
      ItemMetadata
    >();

    private Builder(Material material) {
      this.material = Objects.requireNonNull(material, "material");
      if (!material.isItem() || material.isAir()) {
        throw new IllegalArgumentException(
          "Base material must be a non-air item"
        );
      }
      this.maximumStackSize = material.getMaxStackSize();
    }

    public Builder maximumStackSize(int value) {
      if (value < 1 || value > 64) {
        throw new IllegalArgumentException("maximumStackSize must be 1..64");
      }
      this.maximumStackSize = value;
      return this;
    }

    public Builder quality(Quality value) {
      this.quality = Objects.requireNonNull(value, "quality");
      return this;
    }

    public Builder tradeable(boolean value) {
      this.tradeable = value;
      return this;
    }

    public Builder placeable(boolean value) {
      this.placeable = value;
      return this;
    }

    public Builder category(String value) {
      this.category = Objects.requireNonNull(value, "category");
      return this;
    }

    /** Enables the CustomItem hand-tick callback for stacks in either hand. */
    public Builder handTicking(boolean value) {
      this.handTicking = value;
      return this;
    }

    /** Enables the CustomItem inventory-tick callback for non-hand player slots. */
    public Builder inventoryTicking(boolean value) {
      this.inventoryTicking = value;
      return this;
    }

    public Builder metadata(
      String key,
      ItemDataType type,
      Object defaultValue
    ) {
      ItemMetadata entry = new ItemMetadata(key, type, defaultValue);
      if (this.metadata.putIfAbsent(key, entry) != null) {
        throw new IllegalArgumentException("Duplicate metadata key: " + key);
      }
      return this;
    }

    public ItemProperties build() {
      return new ItemProperties(this);
    }
  }
}
