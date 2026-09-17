package dev.bisz.city.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.bisz.city.model.City;
import dev.bisz.city.model.Plot;
import dev.bisz.city.model.PlotId;
import org.junit.jupiter.api.Test;

class PlotGridTest {

  private City city(int plotSize) {
    return new City("c", "City", "world", 500L, 86_400_000L, plotSize);
  }

  @Test
  void cellsPerChunkForSizeEight() {
    assertEquals(2, PlotGrid.cellsPerChunk(8));
    assertEquals(4, PlotGrid.cellsPerChunk(4));
    assertEquals(1, PlotGrid.cellsPerChunk(16));
  }

  @Test
  void plotAtMapsBlocksToCells() {
    PlotId id = PlotGrid.plotAt(city(8), 20, 12, 0);
    assertEquals("c", id.cityId());
    assertEquals(2, id.gridX());
    assertEquals(1, id.gridZ());
    assertEquals(0, id.level());
  }

  @Test
  void plotBoundsFollowGridAndSize() {
    Plot plot = PlotGrid.vacant(city(8), new PlotId("c", 2, 1, 0));
    assertEquals(16, plot.minX());
    assertEquals(23, plot.maxX());
    assertEquals(8, plot.minZ());
    assertEquals(15, plot.maxZ());
  }

  @Test
  void plotIdsCoverEveryChunkCell() {
    City city = city(8);
    city.chunks().add(new dev.bisz.city.model.ChunkKey("world", 0, 0));
    assertEquals(4, PlotGrid.plotIds(city).size());
  }
}
