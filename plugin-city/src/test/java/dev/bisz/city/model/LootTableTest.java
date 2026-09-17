package dev.bisz.city.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LootTableTest {

  private LootTable table() {
    return new LootTable(
      "test",
      3,
      List.of(
        new LootEntry("diamond", 1, 2, 1.0, false),
        new LootEntry("tnt", 1, 1, 1.0, true)
      )
    );
  }

  @Test
  void rollProducesRequestedNumberOfStacks() {
    List<LootTable.LootStack> stacks = table().roll(new Random(42L));
    assertEquals(3, stacks.size());
    for (LootTable.LootStack stack : stacks) {
      assertTrue(Set.of("diamond", "tnt").contains(stack.itemId()));
      assertTrue(stack.amount() >= 1);
    }
  }

  @Test
  void contrabandFlagIsCarriedThrough() {
    List<LootTable.LootStack> stacks = table().roll(new Random(7L));
    for (LootTable.LootStack stack : stacks) {
      assertEquals(stack.itemId().equals("tnt"), stack.contraband());
    }
  }

  @Test
  void emptyTableRollsNothing() {
    LootTable empty = new LootTable("empty", 3, List.of());
    assertTrue(empty.roll(new Random(1L)).isEmpty());
  }
}
