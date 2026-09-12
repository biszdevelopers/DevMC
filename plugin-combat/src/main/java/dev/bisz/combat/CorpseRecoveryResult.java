package dev.bisz.combat;

import java.util.List;
import org.bukkit.inventory.ItemStack;

public record CorpseRecoveryResult(
  List<ItemStack> recovered,
  List<ItemStack> lost
) {
  public CorpseRecoveryResult {
    recovered = clone(recovered);
    lost = clone(lost);
  }

  private static List<ItemStack> clone(List<ItemStack> in) {
    return in.stream().map(ItemStack::clone).toList();
  }
}
