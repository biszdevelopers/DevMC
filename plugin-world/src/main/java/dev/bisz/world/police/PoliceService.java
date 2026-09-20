package dev.bisz.world.police;

import dev.bisz.world.model.CrimeType;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Enforcement contract. The MVP implementation tracks heat and cancels PvP;
 * a lethal sentry NPC can be added later behind this same interface.
 */
public interface PoliceService {

  /** Records a crime by an offender at a location. */
  void report(Player offender, CrimeType crime, Location where);

  /** Whether the player is currently wanted. */
  boolean isWanted(UUID playerId);

  /** The player's accumulated heat, or zero. */
  int heat(UUID playerId);

  /** Clears a player's heat, for example on death or after a fine. */
  void clear(UUID playerId);

  /** Drops expired wanted states. */
  void prune();
}
