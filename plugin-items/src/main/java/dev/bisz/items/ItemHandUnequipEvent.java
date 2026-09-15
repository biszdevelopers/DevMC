package dev.bisz.items;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;

/** Fired after an ItemLib item stops being active in either of a player's hands. */
public final class ItemHandUnequipEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final EquipmentSlot hand;
  private final DevItemStack item;

  public ItemHandUnequipEvent(Player player, EquipmentSlot hand, DevItemStack item) {
    this.player = Objects.requireNonNull(player, "player");
    this.hand = requireHand(hand);
    this.item = Objects.requireNonNull(item, "item");
  }

  public Player player() { return player; }
  public EquipmentSlot hand() { return hand; }
  public DevItemStack item() { return item; }
  public Player getPlayer() { return player; }
  public EquipmentSlot getHand() { return hand; }
  public DevItemStack getItem() { return item; }

  @Override public HandlerList getHandlers() { return HANDLERS; }
  public static HandlerList getHandlerList() { return HANDLERS; }

  private static EquipmentSlot requireHand(EquipmentSlot hand) {
    Objects.requireNonNull(hand, "hand");
    if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND)
      throw new IllegalArgumentException("Item hand events only support HAND and OFF_HAND");
    return hand;
  }
}
