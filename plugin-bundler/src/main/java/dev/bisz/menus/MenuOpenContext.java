package dev.bisz.menus;

import org.bukkit.entity.Player;

/** Context delivered after a menu has opened. */
public final class MenuOpenContext {

  private final Player player;
  private final MenuSession session;

  MenuOpenContext(Player player, MenuSession session) {
    this.player = player;
    this.session = session;
  }

  /** Returns the player whose inventory was opened. */
  public Player player() {
    return player;
  }

  /** Returns the newly active session. */
  public MenuSession session() {
    return session;
  }
}
