package dev.bisz.world.wilderness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bisz.world.model.ChunkKey;
import org.junit.jupiter.api.Test;

class ChunkStateTest {

  private ChunkState state() {
    return new ChunkState(new ChunkKey("world", 0, 0), 0L);
  }

  @Test
  void freshChunkIsFullyResourced() {
    assertEquals(1.0, state().resource(), 1.0E-9);
  }

  @Test
  void depletionLowersResourceFraction() {
    ChunkState state = state();
    state.markRegenerated(0L, 10);
    state.depleteNodes(4);
    assertEquals(0.6, state.resource(), 1.0E-9);
    state.depleteNodes(100);
    assertEquals(0.0, state.resource(), 1.0E-9);
  }

  @Test
  void visibilityDecaysByHalfLife() {
    ChunkState state = state();
    state.bumpVisibility(1.0, 0L);
    state.decayVisibility(1_000L, 1_000L);
    assertEquals(0.5, state.visibility(), 1.0E-9);
    state.decayVisibility(2_000L, 1_000L);
    assertEquals(0.25, state.visibility(), 1.0E-9);
  }

  @Test
  void visibilityIsCappedAtOne() {
    ChunkState state = state();
    state.bumpVisibility(0.8, 0L);
    state.bumpVisibility(0.8, 0L);
    assertEquals(1.0, state.visibility(), 1.0E-9);
  }

  @Test
  void markRegeneratedResetsIndicators() {
    ChunkState state = state();
    state.bumpVisibility(1.0, 0L);
    state.markRegenerated(500L, 12);
    assertEquals(0.0, state.visibility(), 1.0E-9);
    assertEquals(12, state.baselineNodes());
    assertEquals(12, state.remainingNodes());
    assertEquals(1.0, state.resource(), 1.0E-9);
    assertEquals(500L, state.lastRegenAt());
  }

  @Test
  void stateRoundTripsThroughMap() {
    ChunkState state = state();
    state.markRegenerated(100L, 20);
    state.depleteNodes(5);
    state.bumpVisibility(0.7, 200L);
    ChunkState restored = ChunkState.fromMap(state.toMap());
    assertEquals(state.key(), restored.key());
    assertEquals(state.visibility(), restored.visibility(), 1.0E-9);
    assertEquals(state.baselineNodes(), restored.baselineNodes());
    assertEquals(state.remainingNodes(), restored.remainingNodes());
    assertEquals(state.lastRegenAt(), restored.lastRegenAt());
  }

  @Test
  void depletedChunkWithoutBaselineStaysRich() {
    ChunkState state = state();
    state.depleteNodes(5);
    assertTrue(state.resource() >= 1.0);
  }
}
