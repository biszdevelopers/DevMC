package dev.bisz.combat;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface CombatService {
  boolean hasPvpEnabled(Player player);
  boolean isInPvp(Player player);
  boolean isGhost(Player player);
  Optional<Location> deathAnchor(Player player);
  boolean revive(Player player);
}
