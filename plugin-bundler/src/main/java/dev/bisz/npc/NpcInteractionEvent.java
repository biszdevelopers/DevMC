package dev.bisz.npc;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class NpcInteractionEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final NpcHandle npc;

  public NpcInteractionEvent(Player p, NpcHandle n) {
    player = p;
    npc = n;
  }

  public Player player() {
    return player;
  }

  public NpcHandle npc() {
    return npc;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
