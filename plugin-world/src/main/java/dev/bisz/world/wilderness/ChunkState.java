package dev.bisz.world.wilderness;

import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.storage.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Regeneration indicators for one chunk.
 *
 * <p>Regeneration is tracked per chunk; there is no larger grouping. Two
 * indicators drive scheduling:
 * <ul>
 *   <li><b>Resource</b> — the fraction of ore and loot nodes still present.
 *       Reaches {@code 0} as players mine and loot, marking the chunk as
 *       depleted.</li>
 *   <li><b>Visibility</b> — recent player attention, decaying over time. High
 *       visibility keeps a chunk from being reset out from under players.</li>
 * </ul>
 */
public final class ChunkState {

  private final ChunkKey key;
  private double visibility;
  private long lastUpdate;
  private long lastRegenAt;
  private int baselineNodes;
  private int remainingNodes;

  public ChunkState(ChunkKey key, long now) {
    this.key = Objects.requireNonNull(key, "key");
    this.lastUpdate = now;
    this.lastRegenAt = now;
    this.baselineNodes = 0;
    this.remainingNodes = 0;
  }

  public ChunkKey key() {
    return key;
  }

  public double visibility() {
    return visibility;
  }

  public long lastRegenAt() {
    return lastRegenAt;
  }

  public int baselineNodes() {
    return baselineNodes;
  }

  public int remainingNodes() {
    return remainingNodes;
  }

  /** The fraction of resource nodes still present, from 0 to 1. */
  public double resource() {
    if (baselineNodes <= 0) return 1.0;
    return clamp01((double) remainingNodes / baselineNodes);
  }

  /** Decays visibility toward zero using a half-life. */
  public void decayVisibility(long now, long halfLifeMillis) {
    long elapsed = now - lastUpdate;
    if (elapsed <= 0L) return;
    if (halfLifeMillis <= 0L) {
      visibility = 0.0;
    } else {
      visibility *= Math.pow(0.5, (double) elapsed / halfLifeMillis);
      if (visibility < 1.0E-4) visibility = 0.0;
    }
    lastUpdate = now;
  }

  /** Adds player attention, capped at one. */
  public void bumpVisibility(double amount, long now) {
    if (amount <= 0.0) return;
    visibility = clamp01(visibility + amount);
    lastUpdate = now;
  }

  /** Removes resource nodes as players mine and loot. */
  public void depleteNodes(int nodes) {
    if (nodes <= 0) return;
    remainingNodes = Math.max(0, remainingNodes - nodes);
  }

  /** Resets the chunk after a regeneration pass. */
  public void markRegenerated(long now, int baseline) {
    this.lastRegenAt = now;
    this.lastUpdate = now;
    this.visibility = 0.0;
    this.baselineNodes = Math.max(0, baseline);
    this.remainingNodes = this.baselineNodes;
  }

  /** Serializes this state into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", key.encode());
    map.put("visibility", visibility);
    map.put("last_update", lastUpdate);
    map.put("last_regen_at", lastRegenAt);
    map.put("baseline_nodes", baselineNodes);
    map.put("remaining_nodes", remainingNodes);
    return map;
  }

  /** Restores a state from its stored representation. */
  public static ChunkState fromMap(Map<String, Object> map) {
    ChunkKey key = ChunkKey.decode(Json.string(map, "id", ""));
    ChunkState state = new ChunkState(
      key,
      Json.longValue(map, "last_update", 0L)
    );
    state.visibility = clamp01(Json.decimal(map, "visibility", 0.0));
    state.lastUpdate = Json.longValue(map, "last_update", 0L);
    state.lastRegenAt = Json.longValue(map, "last_regen_at", 0L);
    state.baselineNodes = Math.max(
      0,
      Json.integer(map, "baseline_nodes", 0)
    );
    state.remainingNodes = Math.max(
      0,
      Json.integer(map, "remaining_nodes", state.baselineNodes)
    );
    return state;
  }

  private static double clamp01(double value) {
    if (value < 0.0) return 0.0;
    if (value > 1.0) return 1.0;
    return value;
  }
}
