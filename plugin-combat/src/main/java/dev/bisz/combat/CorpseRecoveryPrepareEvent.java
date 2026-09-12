package dev.bisz.combat;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CorpseRecoveryPrepareEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();
  private final UUID owner;
  private double chance;

  public CorpseRecoveryPrepareEvent(UUID owner, double chance) {
    this.owner = owner;
    setChance(chance);
  }

  public UUID owner() {
    return owner;
  }

  public double chance() {
    return chance;
  }

  public void setChance(double value) {
    if (value < 0 || value > 1) throw new IllegalArgumentException(
      "chance must be 0..1"
    );
    chance = value;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
