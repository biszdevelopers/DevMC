package dev.bisz.city.police;

import dev.bisz.city.economy.Contraband;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Removes contraband from players entering policed space. */
public final class SearchService {

  /** The outcome of a search. */
  public record Result(int confiscated) {}

  /** Confiscates every contraband stack carried by the player. */
  public Result confiscate(Player player) {
    PlayerInventory inventory = player.getInventory();
    int count = 0;
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      ItemStack stack = inventory.getItem(slot);
      if (stack == null || !Contraband.isContraband(stack)) continue;
      count += stack.getAmount();
      inventory.setItem(slot, null);
    }
    ItemStack offhand = inventory.getItemInOffHand();
    if (offhand != null && Contraband.isContraband(offhand)) {
      count += offhand.getAmount();
      inventory.setItemInOffHand(null);
    }
    return new Result(count);
  }
}
