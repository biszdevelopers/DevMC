package dev.bisz.world.model;

import java.util.Objects;

/**
 * Identifies a plot inside a settlement grid.
 *
 * <p>{@code level} is reserved for vertical apartment units and is always zero
 * in the MVP.
 */
public record PlotId(String settlementId, int gridX, int gridZ, int level) {

  public PlotId {
    Objects.requireNonNull(settlementId, "settlementId");
  }

  /** Encodes this id as a stable single-token string. */
  public String encode() {
    return settlementId + ":" + gridX + ":" + gridZ + ":" + level;
  }

  /** Decodes an id produced by {@link #encode()}. */
  public static PlotId decode(String value) {
    Objects.requireNonNull(value, "value");
    String[] parts = value.split(":");
    if (parts.length != 4) {
      throw new IllegalArgumentException("Invalid plot id: " + value);
    }
    return new PlotId(
      parts[0],
      Integer.parseInt(parts[1]),
      Integer.parseInt(parts[2]),
      Integer.parseInt(parts[3])
    );
  }

  @Override
  public String toString() {
    return encode();
  }
}
