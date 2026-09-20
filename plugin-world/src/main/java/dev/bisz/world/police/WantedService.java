package dev.bisz.world.police;

import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.model.CrimeType;
import dev.bisz.world.model.WantedState;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Heat- and wanted-based implementation of the police contract. */
public final class WantedService implements PoliceService {

  private final WorldSettings settings;
  private final Map<UUID, WantedState> wanted = new HashMap<>();

  public WantedService(WorldSettings settings) {
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  @Override
  public void report(Player offender, CrimeType crime, Location where) {
    if (offender == null || crime == null) return;
    WantedState state = wanted.computeIfAbsent(
      offender.getUniqueId(),
      WantedState::new
    );
    state.addCrime(crime, settings.policeWantedMillis(), System.currentTimeMillis());
  }

  @Override
  public boolean isWanted(UUID playerId) {
    WantedState state = wanted.get(playerId);
    return state != null && state.isWanted(System.currentTimeMillis());
  }

  @Override
  public int heat(UUID playerId) {
    WantedState state = wanted.get(playerId);
    if (state == null || !state.isWanted(System.currentTimeMillis())) return 0;
    return state.heat();
  }

  @Override
  public void clear(UUID playerId) {
    wanted.remove(playerId);
  }

  /** Removes expired wanted states to bound memory. */
  public void prune() {
    long now = System.currentTimeMillis();
    wanted.values().removeIf(state -> !state.isWanted(now));
  }
}
