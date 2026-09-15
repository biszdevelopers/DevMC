package dev.bisz.items;

import java.util.Objects;
import org.bukkit.entity.Player;

/** Runtime timestamp paired with a declarative ability; it owns no ticking or event behavior. */
public final class AbilityCooldown {
  private final Ability ability;
  private final long readyAtMillis;

  private AbilityCooldown(Ability ability, long readyAtMillis) {
    this.ability = Objects.requireNonNull(ability, "ability");
    if (ability.cooldownSeconds() <= 0D)
      throw new IllegalArgumentException("Only abilities with a positive cooldown can start a cooldown");
    this.readyAtMillis = readyAtMillis;
  }

  public static AbilityCooldown start(Ability ability, long nowMillis) {
    Objects.requireNonNull(ability, "ability");
    return new AbilityCooldown(ability, nowMillis + Math.round(ability.cooldownSeconds() * 1_000D));
  }

  public Ability ability() { return ability; }
  public long readyAtMillis() { return readyAtMillis; }
  public long remainingMillis(long nowMillis) { return Math.max(0L, readyAtMillis - nowMillis); }
  public boolean active(long nowMillis) { return remainingMillis(nowMillis) > 0L; }
  public String actionBar(Player viewer, long nowMillis) {
    return active(nowMillis)
      ? ability.cooldownActionBar(viewer, remainingMillis(nowMillis))
      : ability.readyActionBar(viewer);
  }
}
