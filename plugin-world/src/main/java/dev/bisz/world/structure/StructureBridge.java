package dev.bisz.world.structure;

import java.nio.file.Path;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Isolates the optional WorldEdit dependency so the rest of the structure
 * system never links against it.
 */
public interface StructureBridge {

  /** Whether a paste backend is available. */
  boolean available();

  /** Loads an opaque structure handle from a file, or null on failure. */
  Object load(StructureDefinition definition, Path file);

  /** Pastes a handle at a location and returns the affected bounds, or null. */
  PlacedStructure paste(
    Object handle,
    World world,
    Location location,
    int rotationY,
    boolean ignoreAir,
    boolean copyEntities
  );

  /** Clears every block inside the supplied bounds. */
  void clear(World world, PlacedStructure bounds);

  /** Captures the blocks inside the supplied bounds into a schematic file. */
  boolean save(World world, PlacedStructure bounds, Path file);

  /** A human-readable backend name. */
  String name();
}
