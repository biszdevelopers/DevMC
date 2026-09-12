package dev.bisz.currency;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after an online player's purse balance changes.
 */
public final class PurseUpdateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final CurrencyManager.Purse purse;
    private final Player player;
    private final long change;
    private final long finalAmount;

    public PurseUpdateEvent(CurrencyManager.Purse purse, Player player, long change, long finalAmount) {
        this.purse = Objects.requireNonNull(purse, "purse");
        this.player = Objects.requireNonNull(player, "player");
        this.change = change;
        this.finalAmount = finalAmount;
    }

    /** The purse value immediately before this update. */
    public CurrencyManager.Purse getPurse() {
        return purse;
    }

    public Player getPlayer() {
        return player;
    }

    /** The signed amount added to (positive) or removed from (negative) the purse. */
    public long getChange() {
        return change;
    }

    public long getFinalAmount() {
        return finalAmount;
    }

    public CurrencyManager.Purse getFinalPurse() {
        return new CurrencyManager.Purse(finalAmount);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
