package dev.bisz.world.wilderness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bisz.world.model.ChunkKey;
import org.junit.jupiter.api.Test;

class RegenerationPolicyTest {

  private static final double RESOURCE_THRESHOLD = 0.35;
  private static final double VISIBILITY_THRESHOLD = 0.2;
  private static final long MAX_AGE = 10_000L;

  private ChunkState state() {
    return new ChunkState(new ChunkKey("world", 0, 0), 0L);
  }

  private boolean decide(ChunkState state, long now) {
    return RegenerationPolicy.evaluate(
      state,
      now,
      RESOURCE_THRESHOLD,
      VISIBILITY_THRESHOLD,
      MAX_AGE
    ).regenerate();
  }

  @Test
  void depletedAndIdleChunkRegenerates() {
    ChunkState state = state();
    state.markRegenerated(0L, 10);
    state.depleteNodes(8);
    assertTrue(decide(state, 1_000L));
  }

  @Test
  void visibleChunkIsNotRegeneratedEvenWhenDepleted() {
    ChunkState state = state();
    state.markRegenerated(0L, 10);
    state.depleteNodes(8);
    state.bumpVisibility(0.9, 1_000L);
    assertFalse(decide(state, 1_000L));
  }

  @Test
  void richIdleYoungChunkIsNotRegenerated() {
    ChunkState state = state();
    state.markRegenerated(0L, 10);
    assertFalse(decide(state, 1_000L));
  }

  @Test
  void overAgeChunkRegeneratesEvenWhenRich() {
    ChunkState state = state();
    state.markRegenerated(0L, 10);
    assertTrue(decide(state, MAX_AGE + 1L));
  }

  @Test
  void overAgeChunkStaysWhenVisible() {
    ChunkState state = state();
    state.markRegenerated(0L, 10);
    state.bumpVisibility(0.9, MAX_AGE + 1L);
    assertFalse(decide(state, MAX_AGE + 1L));
  }
}
