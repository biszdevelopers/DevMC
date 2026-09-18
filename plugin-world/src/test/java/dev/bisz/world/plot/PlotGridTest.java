package dev.bisz.world.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.bisz.world.model.Settlement;
import dev.bisz.world.model.Plot;
import dev.bisz.world.model.PlotId;
import org.junit.jupiter.api.Test;

class PlotGridTest {

  private Settlement settlement(int plotSize) {
    return new Settlement("c", "Settlement", "world", 500L, 86_400_000L, plotSize);
  }

  @Test
  void cellsPerChunkForSizeEight() {
    assertEquals(2, PlotGrid.cellsPerChunk(8));
    assertEquals(4, PlotGrid.cellsPerChunk(4));
    assertEquals(1, PlotGrid.cellsPerChunk(16));
  }

  @Test
  void plotAtMapsBlocksToCells() {
    PlotId id = PlotGrid.plotAt(settlement(8), 20, 12, 0);
    assertEquals("c", id.settlementId());
    assertEquals(2, id.gridX());
    assertEquals(1, id.gridZ());
    assertEquals(0, id.level());
  }

  @Test
  void plotBoundsFollowGridAndSize() {
    Plot plot = PlotGrid.vacant(settlement(8), new PlotId("c", 2, 1, 0));
    assertEquals(16, plot.minX());
    assertEquals(23, plot.maxX());
    assertEquals(8, plot.minZ());
    assertEquals(15, plot.maxZ());
  }

  @Test
  void plotIdsCoverEveryChunkCell() {
    Settlement settlement = settlement(8);
    settlement.chunks().add(new dev.bisz.world.model.ChunkKey("world", 0, 0));
    assertEquals(4, PlotGrid.plotIds(settlement).size());
  }
}
