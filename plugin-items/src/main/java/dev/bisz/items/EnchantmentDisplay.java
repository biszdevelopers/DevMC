package dev.bisz.items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Shared ordering and localized line rendering for ItemLib enchantments. */
public final class EnchantmentDisplay {

  public static final String EXTRA_ROLL_METADATA = "enchants:extra_roll";
  private static final Pattern STATISTICAL_NUMBER = Pattern.compile(
    "(?<![\\p{L}\\p{N}_§])[-+−]?\\d+(?:[.,]\\d+)?(?:%|[x×])?(?![\\p{N}_])"
  );

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

  /** Renders and wraps the localized description while retaining color codes. */
  public static List<String> renderDescription(
    DevEnchantment enchantment,
    EnchantmentData data,
    Player viewer
  ) {
    return wrapGray(enchantment.displayDescription(viewer, data), viewer);
  }

  /** Renders a description whose wording depends on the item material. */
  public static List<String> renderDescription(
    DevEnchantment enchantment, EnchantmentData data, Player viewer, Material material
  ) {
    return wrapGray(enchantment.displayDescription(viewer, data, material), viewer);
  }

  /** Wraps gray descriptive lore using ItemLib's locale-sensitive width. */
  public static List<String> wrapGray(String description, Player viewer) {
    int width = isCompactLanguage(ItemTranslations.language(viewer)) ? 18 : 38;
    return wrap("§7" + highlightStatistics(Objects.requireNonNull(description, "description")), width);
  }

  /**
   * Highlights positive and unsigned statistical numbers in green and negative
   * statistical numbers in red, then restores the standard gray description color.
   */
  public static String highlightStatistics(String description) {
    Matcher matcher = STATISTICAL_NUMBER.matcher(Objects.requireNonNull(description, "description"));
    StringBuffer highlighted = new StringBuffer();
    while (matcher.find()) {
      String statistic = matcher.group();
      String color = statistic.startsWith("-") || statistic.startsWith("−") ? "§c" : "§a";
      matcher.appendReplacement(highlighted, Matcher.quoteReplacement(color + statistic + "§7"));
    }
    matcher.appendTail(highlighted);
    return highlighted.toString();
  }

  /** Renders normal enchantment lore, with descriptions while at most five are applied. */
  public static List<String> renderEntries(
    Map<DevEnchantment, EnchantmentData> enchantments,
    Player viewer
  ) {
    return renderEntries(enchantments, viewer, enchantments.size() <= 5);
  }

  /** Renders normal enchantment lore with an explicit description policy. */
  public static List<String> renderEntries(
    Map<DevEnchantment, EnchantmentData> enchantments,
    Player viewer,
    boolean descriptions
  ) {
    List<Map.Entry<DevEnchantment, EnchantmentData>> entries = order(enchantments, viewer);
    ArrayList<String> lore = new ArrayList<>();
    for (int index = 0; index < entries.size(); index++) {
      Map.Entry<DevEnchantment, EnchantmentData> entry = entries.get(index);
      lore.add(renderLine(entry.getKey(), entry.getValue(), viewer));
      if (descriptions) {
        lore.addAll(renderDescription(entry.getKey(), entry.getValue(), viewer));
        if (index + 1 < entries.size()) lore.add("");
      }
    }
    return List.copyOf(lore);
  }

  private static boolean isCompactLanguage(String language) {
    return language != null && (language.startsWith("zh_") || language.startsWith("ja_") || language.startsWith("ko_"));
  }

  private static List<String> wrap(String text, int width) {
    ArrayList<String> lines = new ArrayList<>();
    for (String paragraph : text.split("\\R", -1)) {
      String color = "";
      StringBuilder line = new StringBuilder();
      int visible = 0;
      for (String word : paragraph.split(" ")) {
        int length = ChatColor.stripColor(word).length();
        if (visible > 0 && visible + 1 + length > width) {
          lines.add(line.toString());
          color = ChatColor.getLastColors(line.toString());
          line = new StringBuilder(color).append(word);
          visible = length;
        } else {
          if (visible > 0) { line.append(' '); visible++; }
          line.append(word); visible += length;
        }
      }
      lines.add(line.toString());
    }
    return List.copyOf(lines);
  }

  static boolean extraRoll(Map<org.bukkit.NamespacedKey, Object> metadata) {
    return metadata.entrySet().stream().anyMatch(entry ->
      EXTRA_ROLL_METADATA.equals(entry.getKey().toString()) &&
      Boolean.TRUE.equals(entry.getValue())
    );
  }
}
