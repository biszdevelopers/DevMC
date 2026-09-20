package dev.bisz.worldgen.listener;

import dev.bisz.worldgen.WorldGenPlugin;
import dev.bisz.worldgen.model.ChunkKey;
import dev.bisz.worldgen.wilderness.ChunkState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Feeds the wilderness indicators: any meaningful change to a wilderness chunk
 * marks it dirty, mined ores and looted containers count as extraction, and
 * death drops pin a chunk from regeneration. Player edits arrive through the
 * break/place events; non-player changes (explosions, fire, and mobs that move
 * or destroy blocks) arrive through the block events below. Natural processes
 * such as leaf decay, snow melt, or grass spread are deliberately ignored: they
 * are not griefing and the feature pass would otherwise re-dirty its own output
 * forever.
 */
public final class WildernessListener implements Listener {

  private final WorldGenPlugin plugin;

  public WildernessListener(WorldGenPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Location location = event.getBlock().getLocation();
    if (!isWilderness(location)) return;
    ChunkState state = state(location);
    state.markEdit(now());
    if (isOre(event.getBlock().getType())) {
      state.depleteNodes(1);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent event) {
    Location location = event.getBlock().getLocation();
    if (!isWilderness(location)) return;
    state(location).markEdit(now());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getClickedBlock() == null) return;
    if (!(event.getClickedBlock().getState() instanceof Container)) return;
    Location location = event.getClickedBlock().getLocation();
    if (!isWilderness(location)) return;
    ChunkState state = state(location);
    state.markEdit(now());
    state.depleteNodes(1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(PlayerDeathEvent event) {
    if (event.getDrops().isEmpty()) return;
    Location location = event.getEntity().getLocation();
    if (!isWilderness(location)) return;
    state(location).pin(now() + plugin.settings().regenDropPinMillis());
  }

  /** Creepers, TNT, ghasts, withers, and any other entity explosion. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityExplode(EntityExplodeEvent event) {
    markChanged(event.blockList());
  }

  /** TNT, respawn anchors, beds, and any other block-driven explosion. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockExplode(BlockExplodeEvent event) {
    markChanged(event.blockList());
  }

  /** Mobs that move or destroy blocks; benign block edits are ignored. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityChangeBlock(EntityChangeBlockEvent event) {
    if (!isGriefing(event.getEntityType())) return;
    markChanged(event.getBlock());
  }

  /** Fire consuming a block. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBurn(BlockBurnEvent event) {
    markChanged(event.getBlock());
  }

  /**
   * Mobs whose block changes count as wilderness griefing. Passive animals and
   * villagers (grazing, farming, snow trails, ...) are excluded.
   */
  private static boolean isGriefing(EntityType type) {
    return switch (type) {
      case ENDERMAN, ENDER_DRAGON, WITHER, RAVAGER -> true;
      default -> false;
    };
  }

  /**
   * Marks every distinct wilderness chunk touched by a set of changed blocks,
   * counting destroyed ores as extraction. Used by explosion events, whose
   * blocks can span several chunks.
   */
  private void markChanged(List<Block> blocks) {
    if (blocks == null || blocks.isEmpty()) return;
    long now = now();
    Set<String> touched = new HashSet<>();
    for (Block block : blocks) {
      ChunkKey key = ChunkKey.of(block.getLocation());
      if (!isWildernessChunk(key)) continue;
      ChunkState state = state(block.getLocation());
      if (state.isSettling(now)) continue;
      if (isOre(block.getType())) state.depleteNodes(1);
      if (touched.add(key.encode())) state.markEdit(now);
    }
  }

  private void markChanged(Block block) {
    Location location = block.getLocation();
    if (!isWilderness(location)) return;
    long now = now();
    ChunkState state = state(location);
    if (state.isSettling(now)) return;
    state.markEdit(now);
    if (isOre(block.getType())) state.depleteNodes(1);
  }

  private ChunkState state(Location location) {
    ChunkState state = plugin
      .indicators()
      .getOrCreate(ChunkKey.of(location), now());
    if (state.baselineNodes() <= 0) {
      state.ensureBaseline(
        plugin.features().estimateBaseline(location.getWorld())
      );
    }
    return state;
  }

  private boolean isWilderness(Location location) {
    return isWildernessChunk(ChunkKey.of(location));
  }

  private boolean isWildernessChunk(ChunkKey key) {
    return plugin.settings().managesWorld(key.world());
  }

  private static boolean isOre(Material material) {
    if (material == Material.ANCIENT_DEBRIS) return true;
    return material.name().endsWith("_ORE");
  }

  private static long now() {
    return System.currentTimeMillis();
  }
}
