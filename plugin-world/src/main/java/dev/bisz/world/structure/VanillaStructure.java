package dev.bisz.world.structure;

import dev.bisz.world.storage.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A Minecraft structure available to the plugin's own placement system. */
public record VanillaStructure(
  String id,
  String name,
  StructureType type,
  List<String> biomes
) {
  public VanillaStructure {
    Objects.requireNonNull(id, "id");
    if (name == null) name = id;
    if (type == null) type = StructureType.POI;
    biomes = biomes == null ? List.of() : List.copyOf(biomes);
  }

  /** Serializes this catalog entry into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    map.put("name", name);
    map.put("type", type.name().toLowerCase(java.util.Locale.ROOT));
    map.put("biomes", List.copyOf(biomes));
    return map;
  }

  /** Restores a catalog entry from its stored representation. */
  public static VanillaStructure fromMap(Map<String, Object> map) {
    String id = Json.string(map, "id", null);
    if (id == null) {
      throw new IllegalArgumentException("Vanilla structure entry is missing id");
    }
    return new VanillaStructure(
      id,
      Json.string(map, "name", id),
      StructureType.parse(Json.string(map, "type", null)),
      Json.stringList(map, "biomes")
    );
  }
}
