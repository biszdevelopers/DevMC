package dev.bisz.world.settlement;

import dev.bisz.world.model.ChunkKey;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Fast lookup from a chunk to the settlement that owns it. */
public final class ChunkIndex {

  private final Map<String, String> settlementByChunk = new HashMap<>();

  /** Rebuilds the index from the supplied settlements. */
  public void rebuild(Collection<? extends dev.bisz.world.model.Settlement> settlements) {
    settlementByChunk.clear();
    for (dev.bisz.world.model.Settlement settlement : settlements) {
      for (ChunkKey key : settlement.chunks()) {
        settlementByChunk.putIfAbsent(key.encode(), settlement.id());
      }
    }
  }

  /** Returns the owning settlement id, or null when the chunk is not in a settlement. */
  public String settlementIdFor(ChunkKey key) {
    return settlementByChunk.get(key.encode());
  }

  /** Whether the supplied chunk belongs to any settlement. */
  public boolean isSettlement(ChunkKey key) {
    return settlementByChunk.containsKey(key.encode());
  }
}
