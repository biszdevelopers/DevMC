package dev.bisz.items;

import org.bukkit.entity.Player;

/** Consistent display vocabulary for active and passive ability activation. */
public enum AbilityUsageMethod {
  PASSIVE_HAND(true, "on_hand", "on hand"),
  PASSIVE_INVENTORY(true, "in_inventory", "in inventory"),
  LEFT_CLICK(false, "left_click", "Left Clicking"),
  RIGHT_CLICK(false, "right_click", "Right Clicking"),
  SHIFT_LEFT_CLICK(false, "shift_left_click", "Shift Left Clicking"),
  SHIFT_RIGHT_CLICK(false, "shift_right_click", "Shift Right Clicking"),
  HOLD_LEFT_CLICK(false, "hold_left_click", "Holding Left Click"),
  HOLD_RIGHT_CLICK(false, "hold_right_click", "Holding Right Click"),
  RELEASE_LEFT_CLICK(false, "release_left_click", "Releasing Left Click"),
  RELEASE_RIGHT_CLICK(false, "release_right_click", "Releasing Right Click"),
  REEL_IN(false, "reel_in", "Reeling In"),
  DOUBLE_JUMP(false, "double_jump", "Double Jumping"),
  SNEAK(false, "sneak", "Sneaking"),
  SPRINT(false, "sprint", "Sprinting"),
  DROP_ITEM(false, "drop_item", "Dropping the Item"),
  SWAP_HANDS(false, "swap_hands", "Swapping Hands");

  private final boolean passive;
  private final String key;
  private final String fallback;

  AbilityUsageMethod(boolean passive, String key, String fallback) {
    this.passive = passive;
    this.key = key;
    this.fallback = fallback;
  }

  public boolean passive() { return passive; }

  public String displayName(Player viewer) {
    String prefix = passive ? "ability.context." : "ability.usage.";
    return ItemTranslations.translate(ItemTranslations.language(viewer), prefix + key, fallback);
  }
}
