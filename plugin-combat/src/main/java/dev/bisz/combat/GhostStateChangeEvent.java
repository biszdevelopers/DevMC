package dev.bisz.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class GhostStateChangeEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final boolean ghost;
  private final String cause;

  public GhostStateChangeEvent(Player p, boolean g, String c) {
    player = p;
    ghost = g;
    cause = c;
  }

  public Player player() {
    return player;
  }

  public boolean ghost() {
    return ghost;
  }

  public String cause() {
    return cause;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
