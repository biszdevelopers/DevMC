package dev.bisz.combat;

import java.util.*;
import java.util.random.RandomGenerator;
import org.bukkit.inventory.ItemStack;

public final class CorpseRecoveryRoller {

  private final RandomGenerator random;

  public CorpseRecoveryRoller(RandomGenerator random) {
    this.random = Objects.requireNonNull(random);
  }

  public CorpseRecoveryResult roll(Collection<ItemStack> input, double chance) {
    if (chance < 0 || chance > 1) throw new IllegalArgumentException(
      "chance must be 0..1"
    );
    List<ItemStack> yes = new ArrayList<>(),
      no = new ArrayList<>();
    for (ItemStack stack : input) {
      if (stack == null) continue;
      int y = 0;
      for (int i = 0; i < stack.getAmount(); i++) if (
        random.nextDouble() < chance
      ) y++;
      add(yes, stack, y);
      add(no, stack, stack.getAmount() - y);
    }
    return new CorpseRecoveryResult(yes, no);
  }

  private static void add(List<ItemStack> out, ItemStack base, int amount) {
    while (amount > 0) {
      ItemStack copy = base.clone();
      int part = Math.min(amount, copy.getMaxStackSize());
      copy.setAmount(part);
      out.add(copy);
      amount -= part;
    }
  }
}
