package dev.bisz.enchants;

import java.util.List;
import org.bukkit.block.Block;

/** Internal world matches retained separately from generator-facing statistics. */
record ModifierMatch(
  CustomEnchantingModifier modifier,
  EnchantingModifierData data,
  List<Block> blocks
) {
  ModifierMatch {
    blocks = List.copyOf(blocks);
  }
}
