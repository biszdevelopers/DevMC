package dev.bisz.city.listener;

import dev.bisz.city.CityPlugin;
import dev.bisz.city.model.ChunkKey;
import dev.bisz.city.model.ZoneType;
import dev.bisz.city.wilderness.RegionKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Feeds the wilderness indicators: edits raise visibility, mined ores and
 * looted containers lower the resource indicator, and death drops pin chunks
 * from regeneration.
 */
public final class WildernessListener implements Listener {

  private final CityPlugin plugin;

  public WildernessListener(CityPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Location location = event.getBlock().getLocation();
    if (!isWilderness(location)) return;
    RegionKey region = regionOf(location);
    plugin.indicators().getOrCreate(region, now()).bumpVisibility(
      plugin.settings().visibilityEditBump(),
      now()
    );
    if (isOre(event.getBlock().getType())) {
      plugin.indicators().get(region).depleteNodes(1);
    }
    markDirty(location);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent event) {
    Location location = event.getBlock().getLocation();
    if (!isWilderness(location)) return;
    plugin.indicators().getOrCreate(regionOf(location), now()).bumpVisibility(
      plugin.settings().visibilityEditBump(),
      now()
    );
    markDirty(location);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getClickedBlock() == null) return;
    if (!(event.getClickedBlock().getState() instanceof Container)) return;
    Location location = event.getClickedBlock().getLocation();
    if (!isWilderness(location)) return;
    plugin.indicators().getOrCreate(regionOf(location), now()).depleteNodes(1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(PlayerDeathEvent event) {
    if (event.getDrops().isEmpty()) return;
    Location location = event.getEntity().getLocation();
    if (!isWilderness(location)) return;
    long until = now() + plugin.settings().regenDropPinMillis();
    plugin
      .regenerationScheduler()
      .tracker()
      .pin(ChunkKey.of(location), until);
  }

  private boolean isWilderness(Location location) {
    return plugin.cities().zoneAt(location) == ZoneType.WILDERNESS;
  }

  private RegionKey regionOf(Location location) {
    return RegionKey.of(
      ChunkKey.of(location),
      plugin.settings().regionSize()
    );
  }

  private void markDirty(Location location) {
    plugin
      .regenerationScheduler()
      .tracker()
      .markDirty(ChunkKey.of(location), now());
  }

  private static boolean isOre(Material material) {
    if (material == Material.ANCIENT_DEBRIS) return true;
    return material.name().endsWith("_ORE");
  }

  private static long now() {
    return System.currentTimeMillis();
  }
}
