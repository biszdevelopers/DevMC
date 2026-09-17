package dev.bisz.city.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructureDefinitionTest {

  private StructureDefinition definition() {
    return new StructureDefinition(
      "supply_drop",
      "Supply Drop",
      "supply_drop.schem",
      "event",
      "tier2",
      1200L,
      90,
      true,
      true,
      "supply_drop",
      false
    );
  }

  @Test
  void definitionRoundTripsThroughMap() {
    StructureDefinition restored = StructureDefinition.fromMap(
      definition().toMap()
    );
    assertEquals(definition(), restored);
  }

  @Test
  void rotationIsNormalizedToClockwiseDegrees() {
    StructureDefinition def = new StructureDefinition(
      "a",
      "A",
      "a.schem",
      "event",
      null,
      0L,
      -90,
      false,
      true,
      null,
      false
    );
    assertEquals(270, def.rotationY());
  }

  @Test
  void negativeLootRespawnBecomesNever() {
    StructureDefinition def = new StructureDefinition(
      "a",
      "A",
      "a.schem",
      "event",
      null,
      -5L,
      0,
      false,
      true,
      null,
      false
    );
    assertEquals(0L, def.lootRespawnTicks());
  }

  @Test
  void missingIdOrFileIsRejected() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", "a");
    assertThrows(
      IllegalArgumentException.class,
      () -> StructureDefinition.fromMap(map)
    );
  }

  @Test
  void nullCategoryDefaultsToEvent() {
    Map<String, Object> map = new HashMap<>(definition().toMap());
    map.put("category", null);
    assertEquals("event", StructureDefinition.fromMap(map).category());
  }

  @Test
  void definitionMapContainsExpectedKeys() {
    Map<String, Object> map = definition().toMap();
    assertTrue(map.containsKey("loot_table"));
    assertTrue(map.containsKey("event_type"));
    assertTrue(map.containsKey("persistent"));
  }
}
