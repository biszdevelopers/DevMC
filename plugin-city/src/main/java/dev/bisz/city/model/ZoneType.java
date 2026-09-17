package dev.bisz.city.model;

/** The law and building rules that apply to a piece of land. */
public enum ZoneType {
  /** City land patrolled by police, where PvP is disabled. */
  POLICED,
  /** City land with no police, where PvP is allowed. */
  UNMONITORED,
  /** Lawless, non-buildable land scheduled for regeneration. */
  WILDERNESS,
}
