package dev.bisz.city.wilderness;

/** Pure decision logic that turns region indicators into a regeneration call. */
public final class RegenerationPolicy {

  private RegenerationPolicy() {}

  /** The evaluation of one region at one moment. */
  public record Decision(
    boolean regenerate,
    boolean depleted,
    boolean idle,
    boolean overAge
  ) {}

  /**
   * A region regenerates when it is idle and either depleted of resources or
   * older than the hard age cap.
   *
   * @param resourceThreshold regenerate only when resource is at or below this
   * @param visibilityThreshold regenerate only when visibility is at or below
   *     this
   * @param maxAgeMillis hard cap on how long a region may go without a reset
   */
  public static Decision evaluate(
    RegionState state,
    long now,
    double resourceThreshold,
    double visibilityThreshold,
    long maxAgeMillis
  ) {
    boolean depleted = state.resource() <= resourceThreshold;
    boolean idle = state.visibility() <= visibilityThreshold;
    boolean overAge = now - state.lastRegenAt() >= maxAgeMillis;
    return new Decision(
      idle && (depleted || overAge),
      depleted,
      idle,
      overAge
    );
  }
}
