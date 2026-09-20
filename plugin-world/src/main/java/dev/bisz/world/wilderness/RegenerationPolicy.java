package dev.bisz.world.wilderness;

/**
 * Pure decision logic for the mining-driven, two-stage regeneration model.
 *
 * <p>A chunk is <b>scheduled</b> once it has been farmed: enough nodes extracted
 * or player edits left idle. It is <b>reset</b> when its scheduled time arrives
 * and it is idle, unpinned, and past the grace period.
 */
public final class RegenerationPolicy {

  private RegenerationPolicy() {}

  /** The evaluation of a scheduled chunk at one moment. */
  public record Decision(
    boolean reset,
    boolean scheduled,
    boolean due,
    boolean idle,
    boolean recent,
    boolean pinned
  ) {}

  /**
   * Whether a chunk should be scheduled for a reset: it has had at least
   * {@code depletionNodes} resource nodes extracted, or it holds player edits
   * that have been idle for {@code dirtyInactivityMillis}.
   */
  public static boolean shouldSchedule(
    ChunkState state,
    long now,
    long depletionNodes,
    long dirtyInactivityMillis
  ) {
    if (state.isScheduled()) return false;
    if (state.extractedNodes() >= depletionNodes) return true;
    return state.dirty() && now - state.lastEditAt() >= dirtyInactivityMillis;
  }

  /**
   * Whether the dirty-regen pass may run on a chunk right now: it holds changes
   * not yet regenerated, no non-spectator player is nearby, it is unpinned, and
   * {@code fastDelayMillis} has elapsed since the last change. The proximity
   * check already keeps regeneration away from players, so no separate grace
   * period is needed.
   */
  public static boolean shouldFastRegen(
    ChunkState state,
    long now,
    boolean occupied,
    long fastDelayMillis
  ) {
    if (!state.dirty()) return false;
    if (occupied || state.isPinned(now)) return false;
    if (state.lastEditAt() <= state.lastFastRegenAt()) return false;
    return now - state.lastEditAt() >= fastDelayMillis;
  }

  /**
   * Whether a scheduled chunk may reset right now.
   *
   * @param occupied whether a non-spectator player is within the idle radius
   * @param graceMillis how long after the last presence/edit a reset must wait
   */
  public static Decision evaluate(
    ChunkState state,
    long now,
    boolean occupied,
    long graceMillis
  ) {
    boolean pinned = state.isPinned(now);
    boolean scheduled = state.isScheduled();
    boolean due = state.isDue(now);
    boolean recent = now - state.lastActivityAt() < graceMillis;
    boolean reset = !occupied && !pinned && scheduled && due && !recent;
    return new Decision(reset, scheduled, due, !occupied, recent, pinned);
  }
}
