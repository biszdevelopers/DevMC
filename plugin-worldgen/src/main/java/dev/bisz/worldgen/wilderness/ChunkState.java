package dev.bisz.worldgen.wilderness;

import dev.bisz.worldgen.model.ChunkKey;
import dev.bisz.worldgen.storage.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * All regeneration state for one chunk. One record per chunk drives both
 * regeneration stages:
 *
 * <ul>
 *   <li><b>Changes</b> — {@link #lastEditAt()} is the clock for any change to
 *       the chunk (player or non-player); a dirty chunk is fully regenerated
 *       once it has been idle for the fast-regen delay.</li>
 *   <li><b>Resource</b> — {@link #extractedNodes()} counts ore and loot removed
 *       since the last reset, which schedules an additional reset on the
 *       resource cycle.</li>
 *   <li><b>Due time</b> — {@link #dueAt()} is when the chunk may be reset again.
 *       It is set when the chunk is farmed and cleared when it is reset.</li>
 *   <li><b>Presence</b> and <b>pin</b> gate resets so nothing is reset under a
 *       player or while death drops are on the ground.</li>
 * </ul>
 */
public final class ChunkState {

  /** Window after a reset during which self-inflicted changes are ignored. */
  private static final long SETTLE_MILLIS = 3_000L;

  private final ChunkKey key;
  private long lastRegenAt;
  private long lastFastRegenAt;
  private long dueAt;
  private long lastPresenceAt;
  private long lastEditAt;
  private boolean dirty;
  private long pinnedUntil;
  private int baselineNodes;
  private int extractedNodes;

  public ChunkState(ChunkKey key, long now) {
    this.key = Objects.requireNonNull(key, "key");
    this.lastRegenAt = now;
    this.lastFastRegenAt = now;
    this.dueAt = 0L;
    this.lastPresenceAt = now;
    this.lastEditAt = 0L;
    this.dirty = false;
    this.pinnedUntil = 0L;
    this.baselineNodes = 0;
    this.extractedNodes = 0;
  }

  public ChunkKey key() {
    return key;
  }

  public long lastRegenAt() {
    return lastRegenAt;
  }

  /** When the fast terrain-fill pass last ran on this chunk. */
  public long lastFastRegenAt() {
    return lastFastRegenAt;
  }

  /** When this chunk may next be reset, or {@code 0} when unscheduled. */
  public long dueAt() {
    return dueAt;
  }

  public long lastPresenceAt() {
    return lastPresenceAt;
  }

  public long lastEditAt() {
    return lastEditAt;
  }

  public boolean dirty() {
    return dirty;
  }

  public long pinnedUntil() {
    return pinnedUntil;
  }

  public int baselineNodes() {
    return baselineNodes;
  }

  public int extractedNodes() {
    return extractedNodes;
  }

  /** Records that the fast terrain-fill pass ran. */
  public void markFastRegen(long now) {
    this.lastFastRegenAt = now;
  }

  public int remainingNodes() {
    return Math.max(0, baselineNodes - extractedNodes);
  }

  /** The fraction of resource nodes still present, from 0 to 1. */
  public double resource() {
    if (baselineNodes <= 0) return 1.0;
    return clamp01((double) remainingNodes() / baselineNodes);
  }

  /** The most recent moment a player was present in or edited this chunk. */
  public long lastActivityAt() {
    return Math.max(lastPresenceAt, lastEditAt);
  }

  public boolean isScheduled() {
    return dueAt > 0L;
  }

  public boolean isDue(long now) {
    return dueAt > 0L && now >= dueAt;
  }

  /** Schedules a reset, keeping the earliest pending time. */
  public void schedule(long when) {
    if (dueAt != 0L) return;
    this.dueAt = Math.max(1L, when);
  }

  /** Overwrites the scheduled time, even if one is already set. */
  public void forceSchedule(long when) {
    this.dueAt = Math.max(1L, when);
  }

  public void clearSchedule() {
    this.dueAt = 0L;
  }

  /** Records that a player is present in this chunk. */
  public void markPresence(long now) {
    this.lastPresenceAt = now;
  }

  /** Records a block edit, marking the chunk dirty. */
  public void markEdit(long now) {
    this.lastEditAt = now;
    this.dirty = true;
  }

  /** Clears the presence and edit grace clocks (used by admin diagnostics). */
  public void clearActivity() {
    this.lastPresenceAt = 0L;
    this.lastEditAt = 0L;
  }

  /** Removes resource nodes as players mine and loot. */
  public void depleteNodes(int nodes) {
    if (nodes <= 0) return;
    extractedNodes += nodes;
  }

  /**
   * Seeds the baseline for a chunk generated before resource accounting existed
   * (baseline {@code 0}), so the HUD fraction is meaningful.
   */
  public void ensureBaseline(int baseline) {
    if (baselineNodes > 0) return;
    this.baselineNodes = Math.max(0, baseline);
  }

  /** Pins the chunk from regeneration until the supplied time. */
  public void pin(long until) {
    this.pinnedUntil = Math.max(pinnedUntil, until);
  }

  public boolean isPinned(long now) {
    return pinnedUntil > now;
  }

  /**
   * Whether the chunk was regenerated too recently to trust non-player change
   * events: the feature pass and its terrain can settle for a moment after a
   * reset, and those self-inflicted changes must not re-schedule it.
   */
  public boolean isSettling(long now) {
    return now - lastRegenAt < SETTLE_MILLIS;
  }

  /** Resets every indicator after a full regeneration pass. */
  public void markRegenerated(long now, int baseline) {
    this.lastRegenAt = now;
    this.lastFastRegenAt = now;
    this.dueAt = 0L;
    this.lastPresenceAt = now;
    this.lastEditAt = 0L;
    this.dirty = false;
    this.pinnedUntil = 0L;
    this.baselineNodes = Math.max(0, baseline);
    this.extractedNodes = 0;
  }

  /** Serializes this state into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", key.encode());
    map.put("last_regen_at", lastRegenAt);
    map.put("last_fast_regen_at", lastFastRegenAt);
    map.put("due_at", dueAt);
    map.put("last_presence_at", lastPresenceAt);
    map.put("last_edit_at", lastEditAt);
    map.put("dirty", dirty);
    map.put("pinned_until", pinnedUntil);
    map.put("baseline_nodes", baselineNodes);
    map.put("extracted_nodes", extractedNodes);
    return map;
  }

  /** Restores a state from its stored representation, migrating schema 2. */
  public static ChunkState fromMap(Map<String, Object> map) {
    ChunkKey key = ChunkKey.decode(Json.string(map, "id", ""));
    long lastUpdate = Json.longValue(
      map,
      "last_presence_at",
      Json.longValue(map, "last_update", 0L)
    );
    ChunkState state = new ChunkState(key, lastUpdate);
    state.lastRegenAt = Json.longValue(map, "last_regen_at", lastUpdate);
    state.lastFastRegenAt = Json.longValue(
      map,
      "last_fast_regen_at",
      state.lastRegenAt
    );
    state.dueAt = Math.max(0L, Json.longValue(map, "due_at", 0L));
    state.lastPresenceAt = lastUpdate;
    state.lastEditAt = Json.longValue(map, "last_edit_at", 0L);
    state.dirty = Json.bool(map, "dirty", false);
    state.pinnedUntil = Json.longValue(map, "pinned_until", 0L);
    state.baselineNodes = Math.max(0, Json.integer(map, "baseline_nodes", 0));
    state.extractedNodes = Math.max(
      0,
      Json.integer(
        map,
        "extracted_nodes",
        state.baselineNodes - Json.integer(map, "remaining_nodes", state.baselineNodes)
      )
    );
    return state;
  }

  private static double clamp01(double value) {
    if (value < 0.0) return 0.0;
    if (value > 1.0) return 1.0;
    return value;
  }
}
