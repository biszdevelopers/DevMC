package dev.bisz.combat;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player's PvP-combat status changes, and once per second while
 * that status remains active.
 */
public final class PVPStatusChangeEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();

  private final Player player;
  private final boolean inPvp;
  private final boolean pvpEnabled;
  private final int remainingSeconds;

  public PVPStatusChangeEvent(
    Player player,
    boolean inPvp,
    boolean pvpEnabled,
    int remainingSeconds
  ) {
    this.player = Objects.requireNonNull(player, "player");
    this.inPvp = inPvp;
    this.pvpEnabled = pvpEnabled;
    this.remainingSeconds = remainingSeconds;
  }

  public Player getPlayer() {
    return player;
  }

  public boolean isInPvp() {
    return inPvp;
  }

  /** Whether the player's /pvp toggle currently permits PvP. */
  public boolean isPvpEnabled() {
    return pvpEnabled;
  }

  /**
   * Seconds until the player leaves PvP status; zero when inactive.
   */
  public int getRemainingSeconds() {
    return remainingSeconds;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
