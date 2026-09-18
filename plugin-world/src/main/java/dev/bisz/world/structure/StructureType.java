package dev.bisz.world.structure;

import java.util.Locale;

/** How a structure is placed and whether it persists. */
public enum StructureType {
  /** Placed once, spaced out, on a cleared pad, and exempt from regeneration. */
  MONUMENT,
  /** Placed many times up to a cap and cycled with chunk regeneration. */
  POI;

  /** Parses a stored value, defaulting to {@link #POI}. */
  public static StructureType parse(String value) {
    if (value == null) return POI;
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return POI;
    }
  }
}
