package dev.bisz.stashes;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a durable stash mutation. */
public final class StashChangeEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();
  private final String partition;
  private final UUID owner;

  public StashChangeEvent(String partition, UUID owner) {
    this.partition = partition;
    this.owner = owner;
  }

  public String partition() {
    return partition;
  }

  public UUID owner() {
    return owner;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
