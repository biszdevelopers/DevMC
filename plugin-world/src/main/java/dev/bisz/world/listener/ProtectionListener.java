package dev.bisz.world.listener;

import dev.bisz.world.WorldPlugin;
import dev.bisz.players.locales.Locale;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Denies building and container access on land the player does not control. */
public final class ProtectionListener implements Listener {

  private final WorldPlugin plugin;

  public ProtectionListener(WorldPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (plugin.protection().canBuild(player, event.getBlock().getLocation())) {
      return;
    }
    event.setCancelled(true);
    player.sendMessage(Locale.get(player, "settlement.protection.denied"));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (plugin.protection().canBuild(player, event.getBlock().getLocation())) {
      return;
    }
    event.setCancelled(true);
    player.sendMessage(Locale.get(player, "settlement.protection.denied"));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getClickedBlock() == null) return;
    if (!(event.getClickedBlock().getState() instanceof Container)) return;
    Player player = event.getPlayer();
    if (
      plugin
        .protection()
        .canOpenContainer(player, event.getClickedBlock().getLocation())
    ) return;
    event.setCancelled(true);
    player.sendMessage(Locale.get(player, "settlement.protection.denied"));
  }
}
