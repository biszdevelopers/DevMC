package dev.bisz.world.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A player's accumulated criminal heat and wanted status. */
public final class WantedState {

  private final UUID playerId;
  private final Set<CrimeType> crimes = EnumSet.noneOf(CrimeType.class);
  private int heat;
  private long expiresAt;

  public WantedState(UUID playerId) {
    this.playerId = Objects.requireNonNull(playerId, "playerId");
  }

  public UUID playerId() {
    return playerId;
  }

  public int heat() {
    return heat;
  }

  public long expiresAt() {
    return expiresAt;
  }

  public Set<CrimeType> crimes() {
    return Set.copyOf(crimes);
  }

  /** Records a crime, accumulating heat and extending the wanted window. */
  public void addCrime(CrimeType crime, long wantedMillis, long now) {
    Objects.requireNonNull(crime, "crime");
    this.crimes.add(crime);
    this.heat += crime.heat();
    this.expiresAt = now + Math.max(1L, wantedMillis);
  }

  /** Whether the player is still wanted at the supplied time. */
  public boolean isWanted(long now) {
    return heat > 0 && now < expiresAt;
  }

  /** Clears heat, for example after death or paying a fine. */
  public void clear() {
    this.crimes.clear();
    this.heat = 0;
    this.expiresAt = 0L;
  }
}
