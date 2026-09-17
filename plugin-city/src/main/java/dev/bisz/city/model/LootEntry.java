package dev.bisz.city.model;

import dev.bisz.city.storage.Json;
import java.util.Map;
import java.util.Objects;

/** One weighted item pool entry inside a loot table. */
public record LootEntry(
  String itemId,
  int minAmount,
  int maxAmount,
  double weight,
  boolean contraband
) {
  public LootEntry {
    Objects.requireNonNull(itemId, "itemId");
    if (minAmount < 1) minAmount = 1;
    if (maxAmount < minAmount) maxAmount = minAmount;
    if (weight <= 0.0) weight = 1.0;
  }

  /** Restores a loot entry from its stored representation. */
  public static LootEntry fromMap(Map<String, Object> map) {
    return new LootEntry(
      Json.string(map, "item", "stone"),
      Json.integer(map, "min", 1),
      Json.integer(map, "max", 1),
      Json.decimal(map, "weight", 1.0),
      Json.bool(map, "contraband", false)
    );
  }

  /** Serializes this entry into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    return Map.of(
      "item",
      itemId,
      "min",
      minAmount,
      "max",
      maxAmount,
      "weight",
      weight,
      "contraband",
      contraband
    );
  }
}
