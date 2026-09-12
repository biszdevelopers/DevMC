/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 */
package dev.bisz.items;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ItemInventories {

  private ItemInventories() {}

  public static boolean removeIfPossible(
    Player player,
    Material material,
    int amount
  ) {
    if (amount < 1) {
      throw new IllegalArgumentException("amount must be positive");
    }
    int total = 0;
    for (ItemStack stack : player.getInventory().getContents()) {
      if (stack == null || stack.getType() != material) continue;
      total += stack.getAmount();
    }
    if (total < amount) {
      return false;
    }
    int remaining = amount;
    for (
      int slot = 0;
      slot < player.getInventory().getSize() && remaining > 0;
      ++slot
    ) {
      ItemStack stack = player.getInventory().getItem(slot);
      if (stack == null || stack.getType() != material) continue;
      if (stack.getAmount() <= remaining) {
        remaining -= stack.getAmount();
        player.getInventory().clear(slot);
        continue;
      }
      stack.setAmount(stack.getAmount() - remaining);
      player.getInventory().setItem(slot, stack);
      remaining = 0;
    }
    return true;
  }
}
