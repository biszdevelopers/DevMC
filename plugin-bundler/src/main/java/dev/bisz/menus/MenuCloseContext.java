package dev.bisz.menus;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Context delivered exactly once when a menu session ends. */
public final class MenuCloseContext {

  private final Player player;
  private final MenuSession session;
  private final InventoryCloseEvent event;

  MenuCloseContext(
    Player player,
    MenuSession session,
    InventoryCloseEvent event
  ) {
    this.player = player;
    this.session = session;
    this.event = event;
  }

  /** Returns the player whose menu closed. */
  public Player player() {
    return player;
  }

  /** Returns the session that is being disposed. */
  public MenuSession session() {
    return session;
  }

  /** Returns the Bukkit close event, or {@code null} for programmatic replacement/disposal. */
  public InventoryCloseEvent event() {
    return event;
  }
}
