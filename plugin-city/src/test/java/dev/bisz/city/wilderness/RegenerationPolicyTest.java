package dev.bisz.city.wilderness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegenerationPolicyTest {

  private static final double RESOURCE_THRESHOLD = 0.35;
  private static final double VISIBILITY_THRESHOLD = 0.2;
  private static final long MAX_AGE = 10_000L;

  private RegionState state() {
    return new RegionState(new RegionKey("world", 0, 0), 0L);
  }

  private boolean decide(RegionState state, long now) {
    return RegenerationPolicy.evaluate(
      state,
      now,
      RESOURCE_THRESHOLD,
      VISIBILITY_THRESHOLD,
      MAX_AGE
    ).regenerate();
  }

  @Test
  void depletedAndIdleRegionRegenerates() {
    RegionState state = state();
    state.markRegenerated(0L, 10);
    state.depleteNodes(8);
    assertTrue(decide(state, 1_000L));
  }

  @Test
  void visibleRegionIsNotRegeneratedEvenWhenDepleted() {
    RegionState state = state();
    state.markRegenerated(0L, 10);
    state.depleteNodes(8);
    state.bumpVisibility(0.9, 1_000L);
    assertFalse(decide(state, 1_000L));
  }

  @Test
  void richIdleYoungRegionIsNotRegenerated() {
    RegionState state = state();
    state.markRegenerated(0L, 10);
    assertFalse(decide(state, 1_000L));
  }

  @Test
  void overAgeRegionRegeneratesEvenWhenRich() {
    RegionState state = state();
    state.markRegenerated(0L, 10);
    assertTrue(decide(state, MAX_AGE + 1L));
  }

  @Test
  void overAgeRegionStaysWhenVisible() {
    RegionState state = state();
    state.markRegenerated(0L, 10);
    state.bumpVisibility(0.9, MAX_AGE + 1L);
    assertFalse(decide(state, MAX_AGE + 1L));
  }
}
