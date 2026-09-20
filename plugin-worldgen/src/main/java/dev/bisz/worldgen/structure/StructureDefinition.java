package dev.bisz.worldgen.structure;

import dev.bisz.worldgen.storage.Json;
import java.util.Map;
import java.util.Objects;

/**
 * A small, destructible, regenerable point of interest (POI) and its loot
 * metadata. Large landmarks (monuments) are not stored here; they are defined
 * manually by administrators.
 *
 * @param id stable identifier
 * @param name display name
 * @param file schematic file name inside the structures directory
 * @param category free-form grouping
 * @param lootTableId loot table filled into the structure's containers, or null
 * @param lootRespawnTicks ticks between loot refills, or zero for never
 * @param rotationY clockwise rotation applied on paste, in degrees
 * @param ignoreAir when true, air blocks in the schematic do not overwrite
 * @param copyEntities whether entities stored in the schematic are pasted
 */
public record StructureDefinition(
  String id,
  String name,
  String file,
  String category,
  String lootTableId,
  long lootRespawnTicks,
  int rotationY,
  boolean ignoreAir,
  boolean copyEntities
) {
  public StructureDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(file, "file");
    if (category == null) category = "poi";
    if (lootRespawnTicks < 0L) lootRespawnTicks = 0L;
    rotationY = ((rotationY % 360) + 360) % 360;
  }

  /** Serializes this definition into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    java.util.LinkedHashMap<String, Object> map =
      new java.util.LinkedHashMap<>();
    map.put("id", id);
    map.put("name", name);
    map.put("file", file);
    map.put("category", category);
    map.put("loot_table", lootTableId);
    map.put("loot_respawn_ticks", lootRespawnTicks);
    map.put("rotation_y", rotationY);
    map.put("ignore_air", ignoreAir);
    map.put("copy_entities", copyEntities);
    return map;
  }

  /** Restores a definition from its stored representation. */
  public static StructureDefinition fromMap(Map<String, Object> map) {
    String id = Json.string(map, "id", null);
    String file = Json.string(map, "file", null);
    if (id == null || file == null) {
      throw new IllegalArgumentException(
        "Structure definition is missing id or file"
      );
    }
    return new StructureDefinition(
      id,
      Json.string(map, "name", id),
      file,
      Json.string(map, "category", "poi"),
      Json.string(map, "loot_table", null),
      Json.longValue(map, "loot_respawn_ticks", 0L),
      Json.integer(map, "rotation_y", 0),
      Json.bool(map, "ignore_air", false),
      Json.bool(map, "copy_entities", true)
    );
  }
}
