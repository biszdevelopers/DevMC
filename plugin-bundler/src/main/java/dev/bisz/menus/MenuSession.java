package dev.bisz.menus;

import java.util.Objects;
import java.util.UUID;

/** A live menu belonging to one player. */
public final class MenuSession {

  private final MenuManager manager;
  private final UUID playerId;
  private final UUID sessionId;

  MenuSession(MenuManager manager, UUID playerId, UUID sessionId) {
    this.manager = manager;
    this.playerId = playerId;
    this.sessionId = sessionId;
  }

  /** Returns the owning player's UUID. */
  public UUID playerId() {
    return playerId;
  }

  /** Returns this unique session's UUID. */
  public UUID sessionId() {
    return sessionId;
  }

  /** Returns the current one-based page number. */
  public int page() {
    return manager.page(this);
  }

  /** Replaces a displayed slot until the next refresh or page transition. */
  public MenuSession setItem(int slot, MenuItem item) {
    manager.setItem(this, slot, Objects.requireNonNull(item, "item"));
    return this;
  }

  /** Clears a displayed slot until the next refresh or page transition. */
  public MenuSession removeItem(int slot) {
    manager.removeItem(this, slot);
    return this;
  }

  /** Re-renders the current page from its template and storage provider. */
  public MenuSession refresh() {
    manager.refresh(this);
    return this;
  }

  /** Opens the following page when one exists. */
  public MenuSession nextPage() {
    manager.changePage(this, 1);
    return this;
  }

  /** Opens the preceding page when one exists. */
  public MenuSession previousPage() {
    manager.changePage(this, -1);
    return this;
  }

  /** Closes and disposes this menu. */
  public void close() {
    manager.close(this);
  }
}
