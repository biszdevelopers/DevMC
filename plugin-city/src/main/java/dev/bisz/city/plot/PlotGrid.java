package dev.bisz.city.plot;

import dev.bisz.city.model.ChunkKey;
import dev.bisz.city.model.City;
import dev.bisz.city.model.Plot;
import dev.bisz.city.model.PlotId;
import dev.bisz.city.model.PlotState;
import java.util.ArrayList;
import java.util.List;

/** Pure grid math that maps blocks and chunks to plot cells. */
public final class PlotGrid {

  private PlotGrid() {}

  /** The number of plot cells along one chunk axis. */
  public static int cellsPerChunk(int plotSize) {
    int size = City.normalizePlotSize(plotSize);
    return Math.max(1, 16 / size);
  }

  /** The plot containing a block position at the supplied level. */
  public static PlotId plotAt(
    City city,
    int blockX,
    int blockZ,
    int level
  ) {
    int size = city.plotSize();
    return new PlotId(
      city.id(),
      Math.floorDiv(blockX, size),
      Math.floorDiv(blockZ, size),
      level
    );
  }

  /** Every plot cell covered by the city's chunks at level zero. */
  public static List<PlotId> plotIds(City city) {
    int size = city.plotSize();
    int perChunk = cellsPerChunk(size);
    List<PlotId> ids = new ArrayList<>();
    for (ChunkKey chunk : city.chunks()) {
      for (int cellX = 0; cellX < perChunk; cellX++) {
        for (int cellZ = 0; cellZ < perChunk; cellZ++) {
          ids.add(
            new PlotId(
              city.id(),
              chunk.x() * perChunk + cellX,
              chunk.z() * perChunk + cellZ,
              0
            )
          );
        }
      }
    }
    return List.copyOf(ids);
  }

  /** Creates a fresh vacant plot for the supplied id. */
  public static Plot vacant(City city, PlotId id) {
    return new Plot(
      id.cityId(),
      id.gridX(),
      id.gridZ(),
      id.level(),
      city.plotSize(),
      PlotState.VACANT,
      null,
      0L,
      "residential"
    );
  }
}
