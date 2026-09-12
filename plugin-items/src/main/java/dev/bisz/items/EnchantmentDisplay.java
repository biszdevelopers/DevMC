package dev.bisz.items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/** Shared ordering and localized line rendering for ItemLib enchantments. */
public final class EnchantmentDisplay {

  public static final String EXTRA_ROLL_METADATA = "enchants:extra_roll";

  private EnchantmentDisplay() {}

  /** Returns entries in ItemLib's quality, level, then localized-name order. */
  public static List<Map.Entry<DevEnchantment, EnchantmentData>> order(
    Map<DevEnchantment, EnchantmentData> enchantments,
    Player viewer
  ) {
    ArrayList<Map.Entry<DevEnchantment, EnchantmentData>> result =
      new ArrayList<>(Objects.requireNonNull(enchantments, "enchantments").entrySet());
    result.sort(comparator(viewer));
    return List.copyOf(result);
  }

  /** Comparator matching ItemLib's normal enchantment-lore ordering. */
  public static Comparator<Map.Entry<DevEnchantment, EnchantmentData>> comparator(
    Player viewer
  ) {
    return (left, right) -> {
      int quality = Integer.compare(
        right.getKey().properties().quality().ordinal(),
        left.getKey().properties().quality().ordinal()
      );
      if (quality != 0) return quality;
      int level = Integer.compare(right.getValue().level(), left.getValue().level());
      if (level != 0) return level;
      return left.getKey().displayName(viewer).toLowerCase(Locale.ROOT)
        .compareTo(right.getKey().displayName(viewer).toLowerCase(Locale.ROOT));
    };
  }

  /** Renders a localized lore line, including the extra-roll marker when set. */
  public static String renderLine(
    DevEnchantment enchantment,
    EnchantmentData data,
    Player viewer
  ) {
    Objects.requireNonNull(enchantment, "enchantment");
    Objects.requireNonNull(data, "data");
    if (extraRoll(data.metadata())) {
      String plain = ChatColor.stripColor(enchantment.displayName(viewer));
      return "§e✎ " + plain + " " + RomanNumerals.format(data.level());
    }
    return enchantment.displayLore(viewer, data.level());
  }

  static boolean extraRoll(Map<org.bukkit.NamespacedKey, Object> metadata) {
    return metadata.entrySet().stream().anyMatch(entry ->
      EXTRA_ROLL_METADATA.equals(entry.getKey().toString()) &&
      Boolean.TRUE.equals(entry.getValue())
    );
  }
}
