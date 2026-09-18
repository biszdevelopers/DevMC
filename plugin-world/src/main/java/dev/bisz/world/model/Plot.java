package dev.bisz.world.model;

import dev.bisz.world.storage.Json;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A rentable sub-chunk land unit inside a settlement. */
public final class Plot {

  private final String settlementId;
  private final int gridX;
  private final int gridZ;
  private final int level;
  private final int size;
  private PlotState state;
  private UUID owner;
  private long rentPaidUntil;
  private String zone;

  public Plot(
    String settlementId,
    int gridX,
    int gridZ,
    int level,
    int size,
    PlotState state,
    UUID owner,
    long rentPaidUntil,
    String zone
  ) {
    this.settlementId = Objects.requireNonNull(settlementId, "settlementId");
    this.gridX = gridX;
    this.gridZ = gridZ;
    this.level = level;
    this.size = Math.max(1, size);
    this.state = Objects.requireNonNull(state, "state");
    this.owner = owner;
    this.rentPaidUntil = rentPaidUntil;
    this.zone = Objects.requireNonNull(zone, "zone");
  }

  public PlotId id() {
    return new PlotId(settlementId, gridX, gridZ, level);
  }

  public String settlementId() {
    return settlementId;
  }

  public int gridX() {
    return gridX;
  }

  public int gridZ() {
    return gridZ;
  }

  public int level() {
    return level;
  }

  public int size() {
    return size;
  }

  public PlotState state() {
    return state;
  }

  public void state(PlotState value) {
    this.state = Objects.requireNonNull(value, "state");
  }

  public UUID owner() {
    return owner;
  }

  public void owner(UUID value) {
    this.owner = value;
  }

  public long rentPaidUntil() {
    return rentPaidUntil;
  }

  public void rentPaidUntil(long value) {
    this.rentPaidUntil = value;
  }

  public String zone() {
    return zone;
  }

  public void zone(String value) {
    this.zone = Objects.requireNonNull(value, "zone");
  }

  public int minX() {
    return gridX * size;
  }

  public int minZ() {
    return gridZ * size;
  }

  public int maxX() {
    return minX() + size - 1;
  }

  public int maxZ() {
    return minZ() + size - 1;
  }

  public boolean contains(String world, int blockX, int blockZ) {
    Objects.requireNonNull(world, "world");
    return (
      blockX >= minX() &&
      blockX <= maxX() &&
      blockZ >= minZ() &&
      blockZ <= maxZ()
    );
  }

  /** Serializes this plot into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    java.util.LinkedHashMap<String, Object> map =
      new java.util.LinkedHashMap<>();
    map.put("id", id().encode());
    map.put("settlement_id", settlementId);
    map.put("grid_x", gridX);
    map.put("grid_z", gridZ);
    map.put("level", level);
    map.put("size", size);
    map.put("state", state.name());
    map.put("owner", owner == null ? null : owner.toString());
    map.put("rent_paid_until", rentPaidUntil);
    map.put("zone", zone);
    return map;
  }

  /** Restores a plot from its stored representation. */
  public static Plot fromMap(Map<String, Object> map) {
    PlotId id;
    try {
      id = PlotId.decode(Json.string(map, "id", ""));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid plot id in store", exception);
    }
    String ownerText = Json.string(map, "owner", null);
    UUID owner = null;
    if (ownerText != null && !ownerText.isBlank()) {
      try {
        owner = UUID.fromString(ownerText);
      } catch (IllegalArgumentException ignored) {
        owner = null;
      }
    }
    PlotState state;
    try {
      state = PlotState.valueOf(Json.string(map, "state", "VACANT"));
    } catch (IllegalArgumentException exception) {
      state = PlotState.VACANT;
    }
    return new Plot(
      id.settlementId(),
      id.gridX(),
      id.gridZ(),
      id.level(),
      Json.integer(map, "size", 16),
      state,
      owner,
      Json.longValue(map, "rent_paid_until", 0L),
      Json.string(map, "zone", "residential")
    );
  }
}
