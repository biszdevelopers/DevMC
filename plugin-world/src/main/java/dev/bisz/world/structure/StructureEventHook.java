package dev.bisz.world.structure;

import org.bukkit.entity.Player;

/**
 * Extension point for structure events. External code registers a hook per
 * event type and supplies the gameplay details; the backbone only calls it.
 */
public interface StructureEventHook {

  /** The event type this hook handles, matching a definition's event type. */
  String type();

  /** Called once after a structure is pasted and looted. */
  default void onSpawn(
    StructureInstance instance,
    StructureDefinition definition
  ) {}

  /** Called on the maintenance tick while the structure is active. */
  default void onActive(
    StructureInstance instance,
    StructureDefinition definition,
    long now
  ) {}

  /** Called when a player opens a container inside the structure. */
  default void onLoot(
    StructureInstance instance,
    StructureDefinition definition,
    Player player
  ) {}

  /** Called once before a structure is cleared and removed. */
  default void onDespawn(
    StructureInstance instance,
    StructureDefinition definition
  ) {}

  /** Whether the structure should be despawned now. */
  default boolean shouldDespawn(
    StructureInstance instance,
    StructureDefinition definition,
    long now
  ) {
    return false;
  }
}
