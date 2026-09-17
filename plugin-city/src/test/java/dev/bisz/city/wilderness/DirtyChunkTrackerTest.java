package dev.bisz.city.wilderness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bisz.city.model.ChunkKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirtyChunkTrackerTest {

  private final ChunkKey key = new ChunkKey("world", 0, 0);

  @Test
  void dirtyChunkIsTracked() {
    DirtyChunkTracker tracker = new DirtyChunkTracker();
    tracker.markDirty(key, 1_000L);
    assertTrue(tracker.isDirty(key));
    assertEquals(1, tracker.size());
  }

  @Test
  void chunkIsDueAfterInactivity() {
    DirtyChunkTracker tracker = new DirtyChunkTracker();
    tracker.markDirty(key, 0L);
    List<ChunkKey> due = tracker.due(2_000L, 1_000L, 1_000_000L);
    assertEquals(List.of(key), due);
  }

  @Test
  void pinnedChunkIsNotDue() {
    DirtyChunkTracker tracker = new DirtyChunkTracker();
    tracker.markDirty(key, 0L);
    tracker.pin(key, 10_000L);
    assertTrue(tracker.isPinned(key, 5_000L));
    assertTrue(tracker.due(5_000L, 1_000L, 1_000_000L).isEmpty());
  }

  @Test
  void maxAgeOverridesRecentActivity() {
    DirtyChunkTracker tracker = new DirtyChunkTracker();
    tracker.markDirty(key, 0L);
    tracker.markDirty(key, 5_000L);
    assertFalse(tracker.due(6_500L, 1_000_000L, 6_000L).isEmpty());
  }

  @Test
  void clearForgetsChunk() {
    DirtyChunkTracker tracker = new DirtyChunkTracker();
    tracker.markDirty(key, 0L);
    tracker.clear(key);
    assertFalse(tracker.isDirty(key));
  }
}
