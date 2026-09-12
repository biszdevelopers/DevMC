package dev.bisz.menus;

import org.bukkit.entity.Player;

/** Context delivered after a paged menu changes page. */
public final class MenuPageChangeContext {

  private final Player player;
  private final MenuSession session;
  private final int previousPage;
  private final int page;

  MenuPageChangeContext(
    Player player,
    MenuSession session,
    int previousPage,
    int page
  ) {
    this.player = player;
    this.session = session;
    this.previousPage = previousPage;
    this.page = page;
  }

  /** Returns the viewing player. */
  public Player player() {
    return player;
  }

  /** Returns the active paged session. */
  public MenuSession session() {
    return session;
  }

  /** Returns the previous one-based page. */
  public int previousPage() {
    return previousPage;
  }

  /** Returns the new one-based page. */
  public int page() {
    return page;
  }
}
