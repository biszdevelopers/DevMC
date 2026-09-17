package dev.bisz.city.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelRoundTripTest {

  @Test
  void chunkKeyEncodesAndDecodes() {
    ChunkKey key = new ChunkKey("world", -12, 34);
    assertEquals(key, ChunkKey.decode(key.encode()));
  }

  @Test
  void cityRoundTripsThroughMap() {
    City city = new City("downtown", "Downtown", "world", 750L, 3_600_000L, 8);
    city.chunks().add(new ChunkKey("world", 1, 2));
    city.chunks().add(new ChunkKey("world", 1, 3));
    city.treasury(4200L);
    city.pvpAllowed(true);

    City restored = City.fromMap(city.toMap());
    assertEquals("downtown", restored.id());
    assertEquals("Downtown", restored.name());
    assertEquals(2, restored.chunks().size());
    assertEquals(750L, restored.rentPrice());
    assertEquals(8, restored.plotSize());
    assertEquals(4200L, restored.treasury());
  }

  @Test
  void plotRoundTripsThroughMap() {
    UUID owner = UUID.randomUUID();
    Plot plot = new Plot(
      "downtown",
      3,
      4,
      0,
      8,
      PlotState.RENTED,
      owner,
      123_456L,
      "residential"
    );
    Plot restored = Plot.fromMap(plot.toMap());
    assertEquals(plot.id(), restored.id());
    assertEquals(owner, restored.owner());
    assertEquals(PlotState.RENTED, restored.state());
    assertEquals(123_456L, restored.rentPaidUntil());
    assertEquals(8, restored.size());
  }

  @Test
  void vacantPlotHasNoOwner() {
    Plot plot = new Plot(
      "downtown",
      0,
      0,
      0,
      16,
      PlotState.VACANT,
      null,
      0L,
      "residential"
    );
    assertNull(Plot.fromMap(plot.toMap()).owner());
  }
}
