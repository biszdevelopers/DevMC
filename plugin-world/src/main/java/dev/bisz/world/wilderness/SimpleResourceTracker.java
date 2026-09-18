package dev.bisz.world.wilderness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Tracks mined basic blocks (stone, dirt, sand, ...) so they can be restored
 * quickly, before the slower region-level ore regeneration runs.
 *
 * <p>Blocks players placed themselves are remembered so the quick layer never
 * resurrects a player's own build.
 */
public final class SimpleResourceTracker {

  /** One block waiting to be restored. */
  public record Respawn(
    String key,
    String world,
    int x,
    int y,
    int z,
    BlockData data,
    long dueAt
  ) {}

  private final Map<String, Respawn> pending = new HashMap<>();
  private final Map<String, Long> playerPlaced = new HashMap<>();

  /** Schedules a broken block for restoration at the supplied time. */
  public void record(Block block, long dueAt) {
    String key = key(block);
    pending.put(
      key,
      new Respawn(
        key,
        block.getWorld().getName(),
        block.getX(),
        block.getY(),
        block.getZ(),
        block.getBlockData().clone(),
        dueAt
      )
    );
  }

  /** Remembers that a player placed this block. */
  public void markPlaced(Block block, long now) {
    playerPlaced.put(key(block), now);
  }

  /** Whether a player placed the block recently. */
  public boolean isPlayerPlaced(Block block) {
    return playerPlaced.containsKey(key(block));
  }

  /** Removes a tracked block, for example when a player breaks it again. */
  public void forget(Block block) {
    pending.remove(key(block));
  }

  /** Restorations that are due at the supplied time. */
  public List<Respawn> due(long now) {
    List<Respawn> result = new ArrayList<>();
    for (Respawn entry : pending.values()) {
      if (entry.dueAt() <= now) result.add(entry);
    }
    return result;
  }

  /** Removes a completed restoration. */
  public void complete(String key) {
    pending.remove(key);
  }

  /** Pushes a restoration back by the supplied delay. */
  public void postpone(String key, long now, long delayMillis) {
    Respawn entry = pending.get(key);
    if (entry == null) return;
    pending.put(
      key,
      new Respawn(
        entry.key(),
        entry.world(),
        entry.x(),
        entry.y(),
        entry.z(),
        entry.data(),
        now + delayMillis
      )
    );
  }

  /** Drops stale player-placed markers. */
  public void prunePlaced(long now, long maxAgeMillis) {
    playerPlaced.values().removeIf(placedAt -> now - placedAt > maxAgeMillis);
  }

  /** Number of blocks waiting to be restored. */
  public int pendingCount() {
    return pending.size();
  }

  private static String key(Block block) {
    return (
      block.getWorld().getName() +
      ":" +
      block.getX() +
      ":" +
      block.getY() +
      ":" +
      block.getZ()
    );
  }
}
