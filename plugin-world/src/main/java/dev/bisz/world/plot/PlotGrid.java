package dev.bisz.world.plot;

import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.Plot;
import dev.bisz.world.model.PlotId;
import dev.bisz.world.model.PlotState;
import java.util.ArrayList;
import java.util.List;

/** Pure grid math that maps blocks and chunks to plot cells. */
public final class PlotGrid {

  private PlotGrid() {}

  /** The number of plot cells along one chunk axis. */
  public static int cellsPerChunk(int plotSize) {
    int size = Settlement.normalizePlotSize(plotSize);
    return Math.max(1, 16 / size);
  }

  /** The plot containing a block position at the supplied level. */
  public static PlotId plotAt(
    Settlement settlement,
    int blockX,
    int blockZ,
    int level
  ) {
    int size = settlement.plotSize();
    return new PlotId(
      settlement.id(),
      Math.floorDiv(blockX, size),
      Math.floorDiv(blockZ, size),
      level
    );
  }

  /** Every plot cell covered by the settlement's chunks at level zero. */
  public static List<PlotId> plotIds(Settlement settlement) {
    int size = settlement.plotSize();
    int perChunk = cellsPerChunk(size);
    List<PlotId> ids = new ArrayList<>();
    for (ChunkKey chunk : settlement.chunks()) {
      for (int cellX = 0; cellX < perChunk; cellX++) {
        for (int cellZ = 0; cellZ < perChunk; cellZ++) {
          ids.add(
            new PlotId(
              settlement.id(),
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
  public static Plot vacant(Settlement settlement, PlotId id) {
    return new Plot(
      id.settlementId(),
      id.gridX(),
      id.gridZ(),
      id.level(),
      settlement.plotSize(),
      PlotState.VACANT,
      null,
      0L,
      "residential"
    );
  }
}
