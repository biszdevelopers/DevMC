package dev.bisz.city.city;

import dev.bisz.city.model.ChunkKey;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Fast lookup from a chunk to the city that owns it. */
public final class ChunkIndex {

  private final Map<String, String> cityByChunk = new HashMap<>();

  /** Rebuilds the index from the supplied cities. */
  public void rebuild(Collection<? extends dev.bisz.city.model.City> cities) {
    cityByChunk.clear();
    for (dev.bisz.city.model.City city : cities) {
      for (ChunkKey key : city.chunks()) {
        cityByChunk.putIfAbsent(key.encode(), city.id());
      }
    }
  }

  /** Returns the owning city id, or null when the chunk is not in a city. */
  public String cityIdFor(ChunkKey key) {
    return cityByChunk.get(key.encode());
  }

  /** Whether the supplied chunk belongs to any city. */
  public boolean isCity(ChunkKey key) {
    return cityByChunk.containsKey(key.encode());
  }
}
