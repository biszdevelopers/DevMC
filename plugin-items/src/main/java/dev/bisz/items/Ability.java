package dev.bisz.items;

import java.util.Objects;
import java.util.List;
import org.bukkit.entity.Player;

/** Immutable display definition for one item ability. */
public final class Ability {
  public static final int COOLDOWN_BAR_SEGMENTS = 15;
  private final String localeRoot;
  private final String nameFallback;
  private final String descriptionFallback;
  private final Quality quality;
  private final AbilityUsageMethod usageMethod;
  private final double cooldownSeconds;
  private final double activationIntervalSeconds;

  private Ability(Builder builder) {
    localeRoot = builder.localeRoot;
    nameFallback = builder.nameFallback;
    descriptionFallback = builder.descriptionFallback;
    quality = builder.quality;
    usageMethod = builder.usageMethod;
    cooldownSeconds = builder.cooldownSeconds;
    activationIntervalSeconds = builder.activationIntervalSeconds;
    if (usageMethod.passive()) {
      if (!(activationIntervalSeconds > 0D) || !Double.isFinite(activationIntervalSeconds) || cooldownSeconds != 0D)
        throw new IllegalArgumentException("Passive abilities require a positive activation interval and no cooldown");
    } else if (activationIntervalSeconds != 0D || cooldownSeconds < 0D || !Double.isFinite(cooldownSeconds)) {
      throw new IllegalArgumentException("Active abilities require a finite non-negative cooldown and no activation interval");
    }
  }

  public static Builder builder(String localeRoot) { return new Builder(localeRoot); }
  public String localeRoot() { return localeRoot; }
  public Quality quality() { return quality; }
  public AbilityUsageMethod usageMethod() { return usageMethod; }
  public double cooldownSeconds() { return cooldownSeconds; }
  public double activationIntervalSeconds() { return activationIntervalSeconds; }
  public String displayName(Player viewer) {
    return ItemTranslations.translate(ItemTranslations.language(viewer), localeRoot + ".name", nameFallback);
  }
  public String displayDescription(Player viewer) {
    return ItemTranslations.translate(ItemTranslations.language(viewer), localeRoot + ".description", descriptionFallback);
  }
  /** Formats this ability's active cooldown for the action bar. */
  public String cooldownActionBar(Player viewer, long remainingMillis) {
    long durationMillis = Math.round(cooldownSeconds * 1_000D);
    int filled = durationMillis <= 0L ? 0 : Math.max(0, Math.min(COOLDOWN_BAR_SEGMENTS,
      (int) Math.ceil((double) Math.max(0L, remainingMillis) * COOLDOWN_BAR_SEGMENTS / durationMillis)));
    String bar = "§a" + "|".repeat(filled) + "§8" + "|".repeat(COOLDOWN_BAR_SEGMENTS - filled);
    return ItemTranslations.translate(ItemTranslations.language(viewer), "ability.actionbar.cooldown",
      "%s §8- §e%.1fs §7[%s§7]", quality.colorCode() + displayName(viewer),
      Math.ceil(Math.max(0L, remainingMillis) / 100D) / 10D, bar);
  }
  /** Formats the one-shot ready state shown when this ability's cooldown ends. */
  public String readyActionBar(Player viewer) {
    return ItemTranslations.translate(ItemTranslations.language(viewer), "ability.actionbar.ready",
      "%s §8- §b§lREADY", quality.colorCode() + displayName(viewer));
  }
  /** Joins simultaneously visible ability cooldowns with the standard dark-gray divider. */
  public static String joinActionBars(List<String> entries) { return String.join("§8|", entries); }

  public static final class Builder {
    private final String localeRoot;
    private String nameFallback;
    private String descriptionFallback;
    private Quality quality = Quality.COMMON;
    private AbilityUsageMethod usageMethod;
    private double cooldownSeconds;
    private double activationIntervalSeconds;

    private Builder(String localeRoot) {
      this.localeRoot = Objects.requireNonNull(localeRoot, "localeRoot");
      if (localeRoot.isBlank()) throw new IllegalArgumentException("Ability locale root cannot be blank");
    }
    public Builder name(String fallback) { nameFallback = Objects.requireNonNull(fallback, "fallback"); return this; }
    public Builder description(String fallback) { descriptionFallback = Objects.requireNonNull(fallback, "fallback"); return this; }
    public Builder quality(Quality value) { quality = Objects.requireNonNull(value, "quality"); return this; }
    public Builder usage(AbilityUsageMethod value) { usageMethod = Objects.requireNonNull(value, "usageMethod"); return this; }
    public Builder cooldownSeconds(double value) { cooldownSeconds = value; return this; }
    public Builder activationIntervalSeconds(double value) { activationIntervalSeconds = value; return this; }
    public Ability build() {
      if (nameFallback == null || nameFallback.isBlank()) throw new IllegalStateException("Ability fallback name is required");
      if (descriptionFallback == null || descriptionFallback.isBlank()) throw new IllegalStateException("Ability fallback description is required");
      if (usageMethod == null) throw new IllegalStateException("Ability usage method is required");
      return new Ability(this);
    }
  }
}
