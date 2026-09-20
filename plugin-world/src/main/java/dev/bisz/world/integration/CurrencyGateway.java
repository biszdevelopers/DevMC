package dev.bisz.world.integration;

import dev.bisz.currency.CurrencyManager;
import java.util.Objects;
import java.util.UUID;

/**
 * Thin, defensive wrapper around Currency so a missing or unavailable economy
 * never breaks a settlement operation.
 */
public final class CurrencyGateway {

  /** Whether the Currency plugin is present and initialized. */
  public boolean isAvailable() {
    return CurrencyManager.getInstance() != null;
  }

  /** The player's current balance, or zero when currency is unavailable. */
  public long balance(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    CurrencyManager manager = CurrencyManager.getInstance();
    if (manager == null) return 0L;
    return manager.getPurse(playerId.toString()).amount();
  }

  /** Whether the player can afford the supplied amount. */
  public boolean canAfford(UUID playerId, long amount) {
    if (amount <= 0L) return true;
    return balance(playerId) >= amount;
  }

  /** Removes nits, returning false when the player cannot afford them. */
  public boolean withdraw(UUID playerId, long amount, String reason) {
    Objects.requireNonNull(playerId, "playerId");
    if (amount <= 0L) return true;
    CurrencyManager manager = CurrencyManager.getInstance();
    if (manager == null) return false;
    try {
      manager.adjustPurse(
        playerId,
        CurrencyManager.NitsOperation.REMOVE,
        amount,
        reason
      );
      return true;
    } catch (IllegalArgumentException insufficient) {
      return false;
    }
  }

  /** Adds nits to a player's purse. */
  public void deposit(UUID playerId, long amount, String reason) {
    Objects.requireNonNull(playerId, "playerId");
    if (amount <= 0L) return;
    CurrencyManager manager = CurrencyManager.getInstance();
    if (manager == null) return;
    try {
      manager.adjustPurse(
        playerId,
        CurrencyManager.NitsOperation.GIVE,
        amount,
        reason
      );
    } catch (ArithmeticException overflow) {
      // Balance overflow: drop the deposit rather than corrupting state.
    }
  }
}
