package dev.bisz.world.structure;

import dev.bisz.world.storage.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A placed structure that the event system tracks and can despawn. */
public final class StructureInstance {

  private final String instanceId;
  private final String definitionId;
  private final String eventId;
  private final PlacedStructure bounds;
  private final long placedAt;
  private long lastLootedAt;

  public StructureInstance(
    String instanceId,
    String definitionId,
    String eventId,
    PlacedStructure bounds,
    long placedAt,
    long lastLootedAt
  ) {
    this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
    this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
    this.eventId = eventId;
    this.bounds = Objects.requireNonNull(bounds, "bounds");
    this.placedAt = placedAt;
    this.lastLootedAt = lastLootedAt;
  }

  /** Creates a fresh instance with a random id. */
  public static StructureInstance create(
    String definitionId,
    String eventId,
    PlacedStructure bounds,
    long now
  ) {
    return new StructureInstance(
      UUID.randomUUID().toString(),
      definitionId,
      eventId,
      bounds,
      now,
      now
    );
  }

  public String instanceId() {
    return instanceId;
  }

  public String definitionId() {
    return definitionId;
  }

  public String eventId() {
    return eventId;
  }

  public PlacedStructure bounds() {
    return bounds;
  }

  public long placedAt() {
    return placedAt;
  }

  public long lastLootedAt() {
    return lastLootedAt;
  }

  public void lastLootedAt(long value) {
    this.lastLootedAt = value;
  }

  /** Whether a block position is inside this instance. */
  public boolean contains(String world, int x, int y, int z) {
    return bounds.world().equals(world) && bounds.contains(x, y, z);
  }

  /** Serializes this instance into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>(bounds.toMap());
    map.put("instance_id", instanceId);
    map.put("definition_id", definitionId);
    map.put("event_id", eventId);
    map.put("placed_at", placedAt);
    map.put("last_looted_at", lastLootedAt);
    return map;
  }

  /** Restores an instance from its stored representation. */
  public static StructureInstance fromMap(Map<String, Object> map) {
    String instanceId = Json.string(map, "instance_id", null);
    String definitionId = Json.string(map, "definition_id", null);
    if (instanceId == null || definitionId == null) {
      throw new IllegalArgumentException(
        "Structure instance is missing instance_id or definition_id"
      );
    }
    return new StructureInstance(
      instanceId,
      definitionId,
      Json.string(map, "event_id", null),
      PlacedStructure.fromMap(map),
      Json.longValue(map, "placed_at", 0L),
      Json.longValue(map, "last_looted_at", 0L)
    );
  }
}
