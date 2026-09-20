package dev.bisz.world.wilderness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bisz.world.model.ChunkKey;
import org.junit.jupiter.api.Test;

class ChunkStateTest {

  private ChunkState state() {
    return new ChunkState(new ChunkKey("world", 0, 0), 0L);
  }

  @Test
  void freshChunkIsFullyResourcedAndUnscheduled() {
    ChunkState state = state();
    assertEquals(1.0, state.resource(), 1.0E-9);
    assertFalse(state.isScheduled());
  }

  @Test
  void extractionIsCounted() {
    ChunkState state = state();
    state.ensureBaseline(100);
    state.depleteNodes(30);
    assertEquals(30, state.extractedNodes());
    assertEquals(70, state.remainingNodes());
    assertEquals(0.7, state.resource(), 1.0E-9);
  }

  @Test
  void scheduleKeepsTheEarliestTime() {
    ChunkState state = state();
    state.schedule(1_000L);
    state.schedule(500L);
    assertEquals(1_000L, state.dueAt());
    state.schedule(2_000L);
    assertEquals(1_000L, state.dueAt());
  }

  @Test
  void dueThenResetClearsEverything() {
    ChunkState state = state();
    state.schedule(1_000L);
    assertFalse(state.isDue(999L));
    assertTrue(state.isDue(1_000L));
    state.markRegenerated(2_000L, 50);
    assertFalse(state.isScheduled());
    assertEquals(0, state.extractedNodes());
    assertEquals(50, state.baselineNodes());
    assertEquals(2_000L, state.lastRegenAt());
    assertEquals(2_000L, state.lastFastRegenAt());
    assertFalse(state.dirty());
  }

  @Test
  void settlingWindowFollowsRegeneration() {
    ChunkState state = state();
    state.markRegenerated(1_000L, 10);
    assertTrue(state.isSettling(1_500L));
    assertFalse(state.isSettling(4_500L));
  }

  @Test
  void presenceEditAndPinAreRecorded() {
    ChunkState state = state();
    state.markPresence(123L);
    assertEquals(123L, state.lastPresenceAt());
    state.markEdit(456L);
    assertTrue(state.dirty());
    assertEquals(456L, state.lastEditAt());
    assertEquals(456L, state.lastActivityAt());
    state.pin(5_000L);
    assertTrue(state.isPinned(4_000L));
    assertFalse(state.isPinned(5_000L));
  }

  @Test
  void stateRoundTripsThroughMap() {
    ChunkState state = state();
    state.ensureBaseline(80);
    state.depleteNodes(10);
    state.markEdit(1_000L);
    state.markFastRegen(1_500L);
    state.schedule(2_000L);
    state.pin(3_000L);
    ChunkState restored = ChunkState.fromMap(state.toMap());
    assertEquals(state.key(), restored.key());
    assertEquals(state.baselineNodes(), restored.baselineNodes());
    assertEquals(state.extractedNodes(), restored.extractedNodes());
    assertEquals(state.dueAt(), restored.dueAt());
    assertEquals(state.lastEditAt(), restored.lastEditAt());
    assertEquals(state.lastFastRegenAt(), restored.lastFastRegenAt());
    assertEquals(state.dirty(), restored.dirty());
    assertEquals(state.pinnedUntil(), restored.pinnedUntil());
  }
}
