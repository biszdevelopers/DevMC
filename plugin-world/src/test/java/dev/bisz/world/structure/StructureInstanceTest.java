package dev.bisz.world.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StructureInstanceTest {

  private PlacedStructure bounds() {
    return new PlacedStructure("world", 10, 60, 20, 25, 70, 35);
  }

  @Test
  void createAssignsAnIdAndTimestamps() {
    StructureInstance instance = StructureInstance.create(
      "supply_drop",
      "event-1",
      bounds(),
      1000L
    );
    assertNotNull(instance.instanceId());
    assertEquals("supply_drop", instance.definitionId());
    assertEquals("event-1", instance.eventId());
    assertEquals(1000L, instance.placedAt());
    assertEquals(1000L, instance.lastLootedAt());
  }

  @Test
  void instanceRoundTripsThroughMap() {
    StructureInstance instance = new StructureInstance(
      "id-1",
      "ruined_outpost",
      null,
      bounds(),
      500L,
      700L
    );
    StructureInstance restored = StructureInstance.fromMap(instance.toMap());
    assertEquals(instance.instanceId(), restored.instanceId());
    assertEquals(instance.definitionId(), restored.definitionId());
    assertEquals(instance.bounds(), restored.bounds());
    assertEquals(instance.placedAt(), restored.placedAt());
    assertEquals(instance.lastLootedAt(), restored.lastLootedAt());
  }

  @Test
  void containsMatchesWorldAndBounds() {
    StructureInstance instance = StructureInstance.create(
      "a",
      null,
      bounds(),
      0L
    );
    assertTrue(instance.contains("world", 15, 65, 25));
    assertFalse(instance.contains("world", 9, 65, 25));
    assertFalse(instance.contains("nether", 15, 65, 25));
  }

  @Test
  void lastLootedAtIsMutable() {
    StructureInstance instance = StructureInstance.create(
      "a",
      null,
      bounds(),
      0L
    );
    instance.lastLootedAt(42L);
    assertEquals(42L, instance.lastLootedAt());
  }
}
