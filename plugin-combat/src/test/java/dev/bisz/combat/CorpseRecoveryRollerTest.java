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
    var result = roller().roll(List.of(item(Material.DIAMOND, 17)), 0);
    assertTrue(result.recovered().isEmpty());
    assertEquals(
      17,
      result.lost().stream().mapToInt(ItemStack::getAmount).sum()
    );
  }

  @Test
  void fullChanceRecoversEverything() {
    var result = roller().roll(List.of(item(Material.DIAMOND, 17)), 1);
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
        item(Material.STONE, 64),
        item(Material.DIRT, 31)
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

  private static ItemStack item(Material type, int amount) {
    return new TestItemStack(type, amount);
  }

  private static final class TestItemStack extends ItemStack {
    private final Material type;
    private int amount;

    private TestItemStack(Material type, int amount) {
      super();
      this.type = type;
      this.amount = amount;
    }

    @Override public Material getType() { return type; }
    @Override public int getAmount() { return amount; }
    @Override public void setAmount(int amount) { this.amount = amount; }
    @Override public int getMaxStackSize() { return 64; }
    @Override public ItemStack clone() { return new TestItemStack(type, amount); }
  }
}
