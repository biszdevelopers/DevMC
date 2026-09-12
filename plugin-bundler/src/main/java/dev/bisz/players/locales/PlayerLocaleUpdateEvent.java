/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Event
 *  org.bukkit.event.HandlerList
 *  org.jetbrains.annotations.NotNull
 */
package dev.bisz.players.locales;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PlayerLocaleUpdateEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final String language;
  private final Cause cause;

  public PlayerLocaleUpdateEvent(Player player, String language, Cause cause) {
    this.player = Objects.requireNonNull(player, "player");
    this.language = Objects.requireNonNull(language, "language");
    this.cause = Objects.requireNonNull(cause, "cause");
  }

  public Player player() {
    return this.player;
  }

  public String language() {
    return this.language;
  }

  public Cause cause() {
    return this.cause;
  }

  @NotNull
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  @NotNull
  public static HandlerList getHandlerList() {
    return HANDLERS;
  }

  public static enum Cause {
    PROFILE_LOADED,
    LANGUAGE_CHANGED,
  }
}
