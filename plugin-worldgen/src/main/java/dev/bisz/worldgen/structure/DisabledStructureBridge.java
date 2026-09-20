package dev.bisz.worldgen.structure;

import java.nio.file.Path;
import org.bukkit.Location;
import org.bukkit.World;

/** Fallback bridge used when no paste backend is installed. */
public final class DisabledStructureBridge implements StructureBridge {

  @Override
  public boolean available() {
    return false;
  }

  @Override
  public Object load(StructureDefinition definition, Path file) {
    return null;
  }

  @Override
  public PlacedStructure paste(
    Object handle,
    World world,
    Location location,
    int rotationY,
    boolean ignoreAir,
    boolean copyEntities
  ) {
    return null;
  }

  @Override
  public void clear(World world, PlacedStructure bounds) {
    // Nothing to clear without a backend.
  }

  @Override
  public boolean save(World world, PlacedStructure bounds, Path file) {
    return false;
  }

  @Override
  public String name() {
    return "none";
  }
}
