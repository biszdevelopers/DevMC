package dev.bisz.enchants;

import dev.bisz.items.DevItemStack;
import dev.bisz.items.ItemsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Replaces vanilla grindstone behavior for socketable items: the result is the
 * item with all sockets stripped, and the player is refunded a share of the
 * enchant cost as experience when the result is taken.
 */
final class GrindstoneListener implements Listener {
  private final EnchantsPlugin plugin;

  GrindstoneListener(EnchantsPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onPrepare(PrepareGrindstoneEvent event) {
    Inventory inventory = event.getInventory();
    ItemStack item = socketable(inventory.getItem(0)) ? inventory.getItem(0)
      : socketable(inventory.getItem(1)) ? inventory.getItem(1) : null;
    if (item == null) return;
    Player player = event.getView().getPlayer() instanceof Player viewer ? viewer : null;
    try {
      DevItemStack wrapped = ItemsPlugin.instance().factory().wrap(item.clone());
      if (!(wrapped.definition() instanceof SocketedVanillaItem definition)) return;
      definition.stripAll(wrapped, player);
      event.setResult(wrapped.bukkitStack());
    } catch (RuntimeException ignored) {
      // Leave the vanilla result in place if the socket data is unreadable.
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onTake(InventoryClickEvent event) {
    if (event.getRawSlot() != 2 || !(event.getWhoClicked() instanceof Player player)) return;
    if (!(event.getInventory() instanceof GrindstoneInventory inventory)) return;
    ItemStack result = event.getCurrentItem();
    if (result == null || result.getType().isAir()) return;
    for (int slot = 0; slot <= 1; slot++) {
      ItemStack source = inventory.getItem(slot);
      if (source == null || !socketable(source)) continue;
      refund(player, source);
      return;
    }
  }

  private void refund(Player player, ItemStack source) {
    try {
      DevItemStack wrapped = ItemsPlugin.instance().factory().wrap(source);
      if (!(wrapped.definition() instanceof SocketedVanillaItem definition)) return;
      int filled = definition.normalize(wrapped).size();
      if (filled <= 0) return;
      int refundLevels = plugin.enchantingCosts().refund(source.getType()) * filled;
      if (refundLevels > 0) LinearExperience.addPoints(player, refundLevels * LinearExperience.pointsPerLevel());
    } catch (RuntimeException ignored) {
    }
  }

  private static boolean socketable(ItemStack item) {
    return item != null && !item.getType().isAir() && SocketedVanillaItem.supports(item.getType());
  }
}
