package dev.bisz.world.structure;

import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.model.Monument;
import dev.bisz.world.wilderness.MonumentManager;
import java.util.List;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Places monuments: one copy of each monument definition, spaced around the
 * island, on a cleared pad, and registered as regeneration-exempt.
 */
public final class StructurePlacer {

  private static final long DEFAULT_MONUMENT_RESPAWN_TICKS = 72_000L;

  private final WorldSettings settings;
  private final StructureService structures;
  private final MonumentManager monuments;

  public StructurePlacer(
    WorldSettings settings,
    StructureService structures,
    MonumentManager monuments
  ) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.structures = Objects.requireNonNull(structures, "structures");
    this.monuments = Objects.requireNonNull(monuments, "monuments");
  }

  /** Places every monument definition around a ring of the given radius. */
  public int plantMonuments(World world, int radius) {
    Objects.requireNonNull(world, "world");
    List<StructureDefinition> definitions = structures
      .registry()
      .all()
      .stream()
      .filter(definition -> definition.type() == StructureType.MONUMENT)
      .toList();
    if (definitions.isEmpty()) return 0;
    int placed = 0;
    for (int index = 0; index < definitions.size(); index++) {
      double angle = (2.0 * Math.PI * index) / definitions.size();
      int x = (int) Math.round(Math.cos(angle) * radius);
      int z = (int) Math.round(Math.sin(angle) * radius);
      int y = world.getHighestBlockYAt(x, z) + 1;
      StructureDefinition definition = definitions.get(index);
      clearPad(world, x, y, z, settings.monumentPadRadius());
      StructureInstance instance = structures.spawn(
        definition.id(),
        new Location(world, x + 0.5, y, z + 0.5),
        "monument"
      );
      if (instance == null) continue;
      register(definition, instance);
      placed++;
    }
    return placed;
  }

  private void clearPad(World world, int x, int y, int z, int radius) {
    if (radius <= 0) return;
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        for (int dy = 0; dy <= 8; dy++) {
          world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
        }
        world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.DIRT, false);
      }
    }
  }

  private void register(
    StructureDefinition definition,
    StructureInstance instance
  ) {
    PlacedStructure bounds = instance.bounds();
    String table = definition.lootTableId() == null
      ? "tier1"
      : definition.lootTableId();
    long respawn = definition.lootRespawnTicks() > 0L
      ? definition.lootRespawnTicks()
      : DEFAULT_MONUMENT_RESPAWN_TICKS;
    monuments.add(
      new Monument(
        definition.id(),
        bounds.world(),
        bounds.minX(),
        bounds.minY(),
        bounds.minZ(),
        bounds.maxX(),
        bounds.maxY(),
        bounds.maxZ(),
        1,
        table,
        respawn,
        0.0,
        null
      )
    );
  }
}
