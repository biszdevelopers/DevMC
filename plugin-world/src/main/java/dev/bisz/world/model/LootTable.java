package dev.bisz.world.model;

import dev.bisz.world.storage.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** A weighted set of loot entries rolled into concrete stacks. */
public final class LootTable {

  private final String id;
  private final int rolls;
  private final List<LootEntry> entries;

  public LootTable(String id, int rolls, List<LootEntry> entries) {
    this.id = Objects.requireNonNull(id, "id");
    this.rolls = Math.max(0, rolls);
    this.entries = List.copyOf(entries);
  }

  public String id() {
    return id;
  }

  public int rolls() {
    return rolls;
  }

  public List<LootEntry> entries() {
    return entries;
  }

  /** A concrete rolled stack. */
  public record LootStack(String itemId, int amount, boolean contraband) {}

  /** Rolls this table into concrete stacks using the supplied random source. */
  public List<LootStack> roll(RandomGenerator random) {
    Objects.requireNonNull(random, "random");
    if (entries.isEmpty() || rolls == 0) return List.of();
    double total = entries.stream().mapToDouble(LootEntry::weight).sum();
    List<LootStack> result = new ArrayList<>();
    for (int index = 0; index < rolls; index++) {
      double target = random.nextDouble() * total;
      double cursor = 0.0;
      LootEntry chosen = entries.get(entries.size() - 1);
      for (LootEntry entry : entries) {
        cursor += entry.weight();
        if (target <= cursor) {
          chosen = entry;
          break;
        }
      }
      int amount =
        chosen.minAmount() +
        random.nextInt(chosen.maxAmount() - chosen.minAmount() + 1);
      result.add(
        new LootStack(chosen.itemId(), amount, chosen.contraband())
      );
    }
    return List.copyOf(result);
  }

  /** Restores a loot table from its stored representation. */
  public static LootTable fromMap(Map<String, Object> map) {
    String id = Json.string(map, "id", null);
    if (id == null) {
      throw new IllegalArgumentException("Loot table entry is missing id");
    }
    List<LootEntry> entries = new ArrayList<>();
    for (Map<String, Object> raw : Json.mapList(map, "entries")) {
      entries.add(LootEntry.fromMap(raw));
    }
    return new LootTable(id, Json.integer(map, "rolls", 1), entries);
  }

  /** Serializes this table into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    return Map.of(
      "id",
      id,
      "rolls",
      rolls,
      "entries",
      entries.stream().map(LootEntry::toMap).toList()
    );
  }
}
