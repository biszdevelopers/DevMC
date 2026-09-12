package dev.bisz.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class CorpseRecoveryRollerTest {

  private CorpseRecoveryRoller roller() {
    return new CorpseRecoveryRoller(new Random(42));
  }

  @Test
  void zeroChanceLosesEverything() {
    var result = roller().roll(List.of(new ItemStack(Material.DIAMOND, 17)), 0);
    assertTrue(result.recovered().isEmpty());
    assertEquals(
      17,
      result.lost().stream().mapToInt(ItemStack::getAmount).sum()
    );
  }

  @Test
  void fullChanceRecoversEverything() {
    var result = roller().roll(List.of(new ItemStack(Material.DIAMOND, 17)), 1);
    assertEquals(
      17,
      result.recovered().stream().mapToInt(ItemStack::getAmount).sum()
    );
    assertTrue(result.lost().isEmpty());
  }

  @Test
  void seededHalfChanceConservesUnits() {
    var result = roller().roll(
      List.of(
        new ItemStack(Material.STONE, 64),
        new ItemStack(Material.DIRT, 31)
      ),
      .5
    );
    int recovered = result
      .recovered()
      .stream()
      .mapToInt(ItemStack::getAmount)
      .sum();
    int lost = result.lost().stream().mapToInt(ItemStack::getAmount).sum();
    assertEquals(95, recovered + lost);
    assertTrue(recovered > 0 && lost > 0);
  }
}
