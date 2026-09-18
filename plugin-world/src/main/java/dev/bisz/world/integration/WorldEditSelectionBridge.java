package dev.bisz.world.integration;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.entity.Player;

/** Reads the player's current WorldEdit selection as a chunk region. */
public final class WorldEditSelectionBridge implements SelectionBridge {

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public String name() {
    return "worldedit";
  }

  @Override
  public SelectionRegion selection(Player player) {
    BlockRegion blocks = blockSelection(player);
    if (blocks == null) return null;
    return new SelectionRegion(
      blocks.world(),
      blocks.minX() >> 4,
      blocks.minZ() >> 4,
      blocks.maxX() >> 4,
      blocks.maxZ() >> 4
    );
  }

  @Override
  public BlockRegion blockSelection(Player player) {
    BukkitPlayer actor = BukkitAdapter.adapt(player);
    LocalSession session = WorldEdit
      .getInstance()
      .getSessionManager()
      .get(actor);
    Region region;
    try {
      region = session.getSelection(actor.getWorld());
    } catch (IncompleteRegionException exception) {
      return null;
    }
    BlockVector3 min = region.getMinimumPoint();
    BlockVector3 max = region.getMaximumPoint();
    return new BlockRegion(
      player.getWorld().getName(),
      min.x(),
      min.y(),
      min.z(),
      max.x(),
      max.y(),
      max.z()
    );
  }
}
