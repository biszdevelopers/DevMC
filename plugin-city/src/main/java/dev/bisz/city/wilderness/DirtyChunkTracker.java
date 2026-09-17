package dev.bisz.city.wilderness;

import dev.bisz.city.model.ChunkKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tracks which wilderness chunks are dirty, when they were last touched, and pins. */
public final class DirtyChunkTracker {

  private final Map<ChunkKey, Long> firstTouched = new HashMap<>();
  private final Map<ChunkKey, Long> lastActivity = new HashMap<>();
  private final Map<ChunkKey, Long> pinnedUntil = new HashMap<>();

  /** Marks a chunk dirty and refreshes its activity time. */
  public void markDirty(ChunkKey key, long now) {
    firstTouched.putIfAbsent(key, now);
    lastActivity.put(key, now);
  }

  /** Whether the chunk has unregenerated player edits. */
  public boolean isDirty(ChunkKey key) {
    return firstTouched.containsKey(key);
  }

  /** Pins a chunk from regeneration until the supplied time. */
  public void pin(ChunkKey key, long until) {
    pinnedUntil.merge(key, until, Math::max);
  }

  /** Whether the chunk is currently pinned. */
  public boolean isPinned(ChunkKey key, long now) {
    Long until = pinnedUntil.get(key);
    return until != null && until > now;
  }

  /** Forgets a chunk entirely, after regeneration. */
  public void clear(ChunkKey key) {
    firstTouched.remove(key);
    lastActivity.remove(key);
    pinnedUntil.remove(key);
  }

  /** The number of dirty chunks. */
  public int size() {
    return firstTouched.size();
  }

  /**
   * Dirty chunks that are due for regeneration, oldest activity first.
   *
   * <p>A chunk is due when it has been idle for {@code inactivityMillis}, or
   * when it has been dirty for {@code maxAgeMillis} regardless of activity.
   */
  public List<ChunkKey> due(
    long now,
    long inactivityMillis,
    long maxAgeMillis
  ) {
    List<ChunkKey> result = new ArrayList<>();
    for (ChunkKey key : firstTouched.keySet()) {
      if (isPinned(key, now)) continue;
      long touched = firstTouched.get(key);
      long active = lastActivity.getOrDefault(key, touched);
      if (
        now - active >= inactivityMillis || now - touched >= maxAgeMillis
      ) {
        result.add(key);
      }
    }
    result.sort(Comparator.comparingLong(key -> lastActivity.getOrDefault(key, 0L)));
    return result;
  }
}
