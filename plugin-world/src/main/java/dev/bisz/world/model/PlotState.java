package dev.bisz.world.model;

/** The lifecycle state of a rentable plot. */
public enum PlotState {
  /** Never rented, or reclaimed by the settlement. */
  VACANT,
  /** Currently rented and protected for its owner. */
  RENTED,
  /** Rent expired; protection is gone and contents may be looted. */
  RAIDABLE,
  /** Looted and awaiting reset or re-rent. */
  DERELICT,
}
