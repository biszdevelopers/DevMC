package dev.bisz.world.structure;

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
      "poi",
      "tier2",
      1200L,
      90,
      true,
      true
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
      "poi",
      null,
      0L,
      -90,
      false,
      true
    );
    assertEquals(270, def.rotationY());
  }

  @Test
  void negativeLootRespawnBecomesNever() {
    StructureDefinition def = new StructureDefinition(
      "a",
      "A",
      "a.schem",
      "poi",
      null,
      -5L,
      0,
      false,
      true
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
  void nullCategoryDefaultsToPoi() {
    Map<String, Object> map = new HashMap<>(definition().toMap());
    map.put("category", null);
    assertEquals("poi", StructureDefinition.fromMap(map).category());
  }

  @Test
  void definitionMapContainsExpectedKeys() {
    Map<String, Object> map = definition().toMap();
    assertTrue(map.containsKey("loot_table"));
    assertTrue(map.containsKey("copy_entities"));
  }
}
