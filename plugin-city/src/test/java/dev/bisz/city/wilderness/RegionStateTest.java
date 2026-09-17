package dev.bisz.city.wilderness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegionStateTest {

  private RegionState state() {
    return new RegionState(new RegionKey("world", 0, 0), 0L);
  }

  @Test
  void freshRegionIsFullyResourced() {
    assertEquals(1.0, state().resource(), 1.0E-9);
  }

  @Test
  void depletionLowersResourceFraction() {
    RegionState state = state();
    state.markRegenerated(0L, 10);
    state.depleteNodes(4);
    assertEquals(0.6, state.resource(), 1.0E-9);
    state.depleteNodes(100);
    assertEquals(0.0, state.resource(), 1.0E-9);
  }

  @Test
  void visibilityDecaysByHalfLife() {
    RegionState state = state();
    state.bumpVisibility(1.0, 0L);
    state.decayVisibility(1_000L, 1_000L);
    assertEquals(0.5, state.visibility(), 1.0E-9);
    state.decayVisibility(2_000L, 1_000L);
    assertEquals(0.25, state.visibility(), 1.0E-9);
  }

  @Test
  void visibilityIsCappedAtOne() {
    RegionState state = state();
    state.bumpVisibility(0.8, 0L);
    state.bumpVisibility(0.8, 0L);
    assertEquals(1.0, state.visibility(), 1.0E-9);
  }

  @Test
  void markRegeneratedResetsIndicators() {
    RegionState state = state();
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
    RegionState state = state();
    state.markRegenerated(100L, 20);
    state.depleteNodes(5);
    state.bumpVisibility(0.7, 200L);
    RegionState restored = RegionState.fromMap(state.toMap());
    assertEquals(state.key(), restored.key());
    assertEquals(state.visibility(), restored.visibility(), 1.0E-9);
    assertEquals(state.baselineNodes(), restored.baselineNodes());
    assertEquals(state.remainingNodes(), restored.remainingNodes());
    assertEquals(state.lastRegenAt(), restored.lastRegenAt());
  }

  @Test
  void depletedRegionWithoutBaselineStaysRich() {
    RegionState state = state();
    state.depleteNodes(5);
    assertTrue(state.resource() >= 1.0);
  }
}
