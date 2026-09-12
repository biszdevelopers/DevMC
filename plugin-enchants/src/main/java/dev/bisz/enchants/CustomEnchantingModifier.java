package dev.bisz.enchants;

import dev.bisz.chat.ChatUtils;
import dev.bisz.players.locales.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Base block-backed enchanting modifier with common lifecycle and lore behavior. */
public abstract class CustomEnchantingModifier {
  private final ModifierMode mode;

  protected CustomEnchantingModifier(ModifierMode mode) {
    this.mode = Objects.requireNonNull(mode, "mode");
  }

  public final ModifierMode mode() { return mode; }
  public abstract String id();
  protected abstract boolean matches(Material material);
  protected abstract EnchantingModifierData createData(int blockCount);
  protected abstract int effectPercent(EnchantingModifierData data);
  protected int maximumEffectPercent() { return Integer.MAX_VALUE; }
  protected abstract String nounLocaleKey();
  protected abstract String effectLocaleKey();
  protected abstract String descriptionLocaleKey();

  final ModifierMatch scan(List<Block> blocks) {
    List<Block> matched = blocks.stream().filter(block -> matches(block.getType())).toList();
    return new ModifierMatch(this, createData(matched.size()), matched);
  }

  /** Builds this modifier's block, including the required blank line and indentation. */
  public final List<String> renderLore(Player player, EnchantingModifierData data) {
    ArrayList<String> lines = new ArrayList<>();
    lines.add("");
    String marker = mode == ModifierMode.PERMANENT ? "§d✦" : "§e✌";
    lines.add(Locale.get(player, "enchants.modifier.amount", marker, data.blockCount(), Locale.get(player, nounLocaleKey())));
    int effectPercent = effectPercent(data);
    int maximumPercent = maximumEffectPercent();
    String effect = Locale.get(player, effectLocaleKey());
    if (effectPercent > maximumPercent) {
      lines.add(Locale.get(player, "enchants.modifier.effect.capped", effectPercent, maximumPercent, effect));
    } else if (effectPercent == maximumPercent) {
      lines.add(Locale.get(player, "enchants.modifier.effect.maximum", maximumPercent, effect));
    } else {
      lines.add(Locale.get(player, "enchants.modifier.effect", effectPercent, effect));
    }
    String description = Locale.get(player, descriptionLocaleKey());
    int width = switch (Locale.getLanguage(player)) {
      case ZH_CN, ZH_CN_MOJANG, ZH_TW, JA_JP, KO_KR -> 13;
      default -> 28;
    };
    for (String line : ChatUtils.wrapTextColor(description, width).split("\\R", -1)) {
      lines.add("  §8" + org.bukkit.ChatColor.stripColor(line));
    }
    return List.copyOf(lines);
  }

  final void consume(List<Block> blocks) {
    if (mode == ModifierMode.CONSUMABLE) blocks.forEach(block -> block.setType(Material.AIR));
  }
}
