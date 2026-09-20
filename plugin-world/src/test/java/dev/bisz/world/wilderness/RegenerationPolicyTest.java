package dev.bisz.world.wilderness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bisz.world.model.ChunkKey;
import org.junit.jupiter.api.Test;

class RegenerationPolicyTest {

  private static final long DEPLETION_NODES = 48L;
  private static final long DIRTY_INACTIVITY = 60_000L;
  private static final long GRACE = 100L;

  private ChunkState state() {
    return new ChunkState(new ChunkKey("world", 0, 0), 0L);
  }

  @Test
  void untouchedChunkIsNeverScheduled() {
    assertFalse(
      RegenerationPolicy.shouldSchedule(
        state(),
        1_000L,
        DEPLETION_NODES,
        DIRTY_INACTIVITY
      )
    );
  }

  @Test
  void enoughExtractionSchedules() {
    ChunkState state = state();
    state.depleteNodes((int) DEPLETION_NODES);
    assertTrue(
      RegenerationPolicy.shouldSchedule(
        state,
        1_000L,
        DEPLETION_NODES,
        DIRTY_INACTIVITY
      )
    );
  }

  @Test
  void idleEditsSchedule() {
    ChunkState state = state();
    state.markEdit(0L);
    assertFalse(
      RegenerationPolicy.shouldSchedule(
        state,
        1_000L,
        DEPLETION_NODES,
        DIRTY_INACTIVITY
      )
    );
    assertTrue(
      RegenerationPolicy.shouldSchedule(
        state,
        DIRTY_INACTIVITY + 1L,
        DEPLETION_NODES,
        DIRTY_INACTIVITY
      )
    );
  }

  @Test
  void alreadyScheduledChunkIsNotRescheduled() {
    ChunkState state = state();
    state.schedule(5_000L);
    assertFalse(
      RegenerationPolicy.shouldSchedule(
        state,
        1_000L,
        DEPLETION_NODES,
        DIRTY_INACTIVITY
      )
    );
  }

  @Test
  void fastRegenRequiresChangeIdleUnoccupiedAndUnpinned() {
    ChunkState state = state();
    state.markEdit(1_000L);
    assertFalse(RegenerationPolicy.shouldFastRegen(state, 1_200L, false, 500L));
    assertTrue(RegenerationPolicy.shouldFastRegen(state, 2_000L, false, 0L));
    assertFalse(RegenerationPolicy.shouldFastRegen(state, 2_000L, true, 0L));
    state.pin(3_000L);
    assertFalse(RegenerationPolicy.shouldFastRegen(state, 2_000L, false, 0L));
  }

  @Test
  void fastRegenDoesNotRerunWithoutNewChanges() {
    ChunkState state = state();
    state.markEdit(1_000L);
    state.markFastRegen(2_000L);
    assertFalse(RegenerationPolicy.shouldFastRegen(state, 5_000L, false, 0L));
    state.markEdit(3_000L);
    assertTrue(RegenerationPolicy.shouldFastRegen(state, 5_000L, false, 0L));
  }

  @Test
  void regeneratedChunkIsCleanAndNotEligibleForFastRegen() {
    ChunkState state = state();
    state.markEdit(1_000L);
    assertTrue(RegenerationPolicy.shouldFastRegen(state, 2_000L, false, 0L));
    state.markRegenerated(2_000L, 50);
    assertFalse(state.dirty());
    assertFalse(state.isScheduled());
    assertFalse(RegenerationPolicy.shouldFastRegen(state, 3_000L, false, 0L));
  }

  @Test
  void resetRequiresDueIdleUnpinnedAndPastGrace() {
    ChunkState state = state();
    state.schedule(1_000L);
    assertTrue(RegenerationPolicy.evaluate(state, 2_000L, false, GRACE).reset());

    assertFalse(RegenerationPolicy.evaluate(state, 2_000L, true, GRACE).reset());
    assertFalse(RegenerationPolicy.evaluate(state, 500L, false, GRACE).reset());

    state.markPresence(1_950L);
    assertFalse(RegenerationPolicy.evaluate(state, 2_000L, false, GRACE).reset());

    state.markPresence(0L);
    state.pin(3_000L);
    assertFalse(RegenerationPolicy.evaluate(state, 2_000L, false, GRACE).reset());
  }
}
