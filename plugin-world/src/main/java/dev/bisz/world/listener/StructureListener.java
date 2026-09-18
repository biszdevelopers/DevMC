package dev.bisz.world.listener;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.structure.StructureInstance;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/** Reports container openings inside structure instances to event hooks. */
public final class StructureListener implements Listener {

  private final WorldPlugin plugin;

  public StructureListener(WorldPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getClickedBlock() == null) return;
    if (!(event.getClickedBlock().getState() instanceof Container)) return;
    Location location = event.getClickedBlock().getLocation();
    if (location.getWorld() == null) return;
    StructureInstance instance = plugin
      .structures()
      .instanceAt(
        location.getWorld().getName(),
        location.getBlockX(),
        location.getBlockY(),
        location.getBlockZ()
      );
    if (instance == null) return;
    plugin.structures().notifyLoot(instance, event.getPlayer());
  }
}
