package dev.bisz.items;

import java.util.Objects;

/** Immutable, definition-wide behavior settings for an enchantment. */
public final class EnchantmentProperties {
  private final Quality quality;
  private final boolean handTicking;
  private final boolean inventoryTicking;

  private EnchantmentProperties(Builder builder) {
    quality = builder.quality;
    handTicking = builder.handTicking;
    inventoryTicking = builder.inventoryTicking;
  }

  public static Builder builder() { return new Builder(); }
  public Quality quality() { return quality; }
  public boolean handTicking() { return handTicking; }
  public boolean inventoryTicking() { return inventoryTicking; }

  public static final class Builder {
    private Quality quality = Quality.COMMON;
    private boolean handTicking;
    private boolean inventoryTicking;
    public Builder quality(Quality value) { quality = Objects.requireNonNull(value, "quality"); return this; }
    public Builder handTicking(boolean value) { handTicking = value; return this; }
    public Builder inventoryTicking(boolean value) { inventoryTicking = value; return this; }
    public EnchantmentProperties build() { return new EnchantmentProperties(this); }
  }
}
