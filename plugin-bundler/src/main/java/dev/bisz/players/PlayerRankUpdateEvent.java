package dev.bisz.players;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after an online player's persisted rank changes.
 */
public final class PlayerRankUpdateEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final Rank previous;
  private final Rank current;

  public PlayerRankUpdateEvent(Player player, Rank previous, Rank current) {
    this.player = Objects.requireNonNull(player, "player");
    this.previous = Objects.requireNonNull(previous, "previous");
    this.current = Objects.requireNonNull(current, "current");
  }

  public Player player() {
    return player;
  }

  public Rank previous() {
    return previous;
  }

  public Rank current() {
    return current;
  }

  @Override
  @NotNull
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  @NotNull
  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
