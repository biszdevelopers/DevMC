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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

public final class ItemProperties {

  private final Material material;
  private final int maximumStackSize;
  private final Quality quality;
  private final boolean tradeable;
  private final boolean placeable;
  private final String category;
  private final boolean handTicking;
  private final boolean inventoryTicking;
  private final boolean attackTriggering;
  private final Map<String, ItemMetadata> metadata;
  private final NamespacedKey itemModel;
  private final List<Float> customModelDataFloats;
  private final List<Boolean> customModelDataFlags;
  private final List<String> customModelDataStrings;
  private final List<Color> customModelDataColors;

  private ItemProperties(Builder builder) {
    this.material = builder.material;
    this.maximumStackSize = builder.maximumStackSize;
    this.quality = builder.quality;
    this.tradeable = builder.tradeable;
    this.placeable = builder.placeable;
    this.category = builder.category;
    this.handTicking = builder.handTicking;
    this.inventoryTicking = builder.inventoryTicking;
    this.attackTriggering = builder.attackTriggering;
    this.metadata = Map.copyOf(builder.metadata);
    this.itemModel = builder.itemModel;
    this.customModelDataFloats = List.copyOf(builder.customModelDataFloats);
    this.customModelDataFlags = List.copyOf(builder.customModelDataFlags);
    this.customModelDataStrings = List.copyOf(builder.customModelDataStrings);
    this.customModelDataColors = List.copyOf(builder.customModelDataColors);
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

  /** Whether this item's attack callback should run for accepted melee hits. */
  public boolean attackTriggering() { return this.attackTriggering; }

  public Map<String, ItemMetadata> metadata() {
    return this.metadata;
  }

  /** Resource-pack item model used as this item's client-side appearance. */
  public NamespacedKey itemModel() {
    return this.itemModel;
  }

  public List<Float> customModelDataFloats() {
    return this.customModelDataFloats;
  }

  public List<Boolean> customModelDataFlags() {
    return this.customModelDataFlags;
  }

  public List<String> customModelDataStrings() {
    return this.customModelDataStrings;
  }

  public List<Color> customModelDataColors() {
    return this.customModelDataColors;
  }

  public boolean hasCustomModelData() {
    return !this.customModelDataFloats.isEmpty() ||
      !this.customModelDataFlags.isEmpty() ||
      !this.customModelDataStrings.isEmpty() ||
      !this.customModelDataColors.isEmpty();
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
    private boolean attackTriggering;
    private final Map<String, ItemMetadata> metadata = new LinkedHashMap<
      String,
      ItemMetadata
    >();
    private NamespacedKey itemModel;
    private List<Float> customModelDataFloats = List.of();
    private List<Boolean> customModelDataFlags = List.of();
    private List<String> customModelDataStrings = List.of();
    private List<Color> customModelDataColors = List.of();

    private Builder(Material material) {
      this.material = Objects.requireNonNull(material, "material");
      boolean air = material == Material.AIR ||
        material == Material.CAVE_AIR ||
        material == Material.VOID_AIR;
      if (air || (Bukkit.getServer() != null && !material.isItem())) {
        throw new IllegalArgumentException(
          "Base material must be a non-air item"
        );
      }
      // Paper 26.2 resolves item properties through the live server registry.
      // Pure unit tests have no server; callers may still override this value.
      this.maximumStackSize = Bukkit.getServer() == null
        ? 64
        : material.getMaxStackSize();
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

    /** Enables the DevItem hand-tick callback for behavior-capable definitions. */
    public Builder handTicking(boolean value) {
      this.handTicking = value;
      return this;
    }

    /** Enables the DevItem inventory-tick callback for non-hand player slots. */
    public Builder inventoryTicking(boolean value) {
      this.inventoryTicking = value;
      return this;
    }

    /** Enables the DevItem attack callback for accepted direct melee hits. */
    public Builder attackTriggering(boolean value) {
      this.attackTriggering = value;
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

    /** Selects a resource-pack item model such as {@code devmc:item/test_sword}. */
    public Builder itemModel(NamespacedKey value) {
      this.itemModel = value;
      return this;
    }

    /** Selects a resource-pack item model parsed from {@code namespace:path}. */
    public Builder itemModel(String value) {
      Objects.requireNonNull(value, "itemModel");
      NamespacedKey parsed = NamespacedKey.fromString(value);
      if (parsed == null) {
        throw new IllegalArgumentException(
          "itemModel must be namespace:path: " + value
        );
      }
      return this.itemModel(parsed);
    }

    /** Legacy integer selector, stored as the first custom_model_data float. */
    public Builder customModelData(int value) {
      return this.customModelDataFloats((float) value);
    }

    public Builder customModelDataFloats(Float... values) {
      this.customModelDataFloats = List.of(
        Objects.requireNonNull(values, "customModelDataFloats")
      );
      return this;
    }

    public Builder customModelDataFlags(Boolean... values) {
      this.customModelDataFlags = List.of(
        Objects.requireNonNull(values, "customModelDataFlags")
      );
      return this;
    }

    public Builder customModelDataStrings(String... values) {
      this.customModelDataStrings = List.of(
        Objects.requireNonNull(values, "customModelDataStrings")
      );
      return this;
    }

    public Builder customModelDataColors(Color... values) {
      this.customModelDataColors = List.of(
        Objects.requireNonNull(values, "customModelDataColors")
      );
      return this;
    }

    public ItemProperties build() {
      return new ItemProperties(this);
    }
  }
}
