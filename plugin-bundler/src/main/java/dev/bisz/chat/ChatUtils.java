package dev.bisz.chat;

import dev.bisz.players.locales.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Common formatting and interactive-chat helpers migrated from the legacy Bundler.
 */
public final class ChatUtils {

  private static final String SYSTEM_PREFIX = "§l§dSYSTEM §r§8> §r";
  private static final String CHANNEL_SUFFIX = " §r§8> §r";
  private static final Pattern LEGACY_COLOR = Pattern.compile(
    "(?i)§[0-9A-FK-ORX]"
  );
  private static final String RANDOM_CHARACTERS =
    "abcdefghijklmnopqrstuvwxyz0123456789";

  private ChatUtils() {}

  public enum Colors {
    DARK_BLUE("1"),
    DARK_GREEN("2"),
    DARK_AQUA("3"),
    DARK_RED("4"),
    DARK_PURPLE("5"),
    GOLD("6"),
    GRAY("7"),
    DARK_GRAY("8"),
    BLUE("9"),
    GREEN("a"),
    AQUA("b"),
    RED("c"),
    MAGENTA("d"),
    YELLOW("e"),
    WHITE("f");

    private final String code;

    Colors(String code) {
      this.code = "§" + code;
    }

    @Override
    public String toString() {
      return code;
    }
  }

  public static String emptyColorString(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length cannot be negative");
    }
    StringBuilder colors = new StringBuilder(length * 2);
    Colors[] available = Colors.values();
    for (int index = 0; index < length; index++) {
      colors.append(
        available[ThreadLocalRandom.current().nextInt(available.length)]
      );
    }
    return colors.toString();
  }

  public static void systemMessage(Player player, String message) {
    Objects.requireNonNull(player, "player").sendMessage(
      SYSTEM_PREFIX + Objects.requireNonNull(message, "message")
    );
  }

  public static void systemMessage(
    Player player,
    String channel,
    String message
  ) {
    systemMessage((CommandSender) player, channel, message);
  }

  public static void systemMessage(
    Player player,
    ChatColor color,
    String channel,
    String message
  ) {
    Objects.requireNonNull(player, "player").sendMessage(
      Objects.requireNonNull(color, "color") +
      "§l" +
      channelPrefix(channel) +
      Objects.requireNonNull(message, "message")
    );
  }

  public static void systemMessage(
    CommandSender sender,
    String channel,
    String message
  ) {
    Objects.requireNonNull(sender, "sender").sendMessage(
      channelPrefix(channel) + Objects.requireNonNull(message, "message")
    );
  }

  public static void systemMessage(
    Player player,
    String channel,
    TextComponent content
  ) {
    Objects.requireNonNull(player, "player")
      .spigot()
      .sendMessage(prefixedComponent(channel, content));
  }

  public static void broadcastSystemMessage(String channel, String message) {
    TextComponent content = new TextComponent(
      TextComponent.fromLegacyText(Objects.requireNonNull(message, "message"))
    );
    broadcastSystemMessage(channel, content);
  }

  public static void broadcastSystemMessage(
    String channel,
    TextComponent content
  ) {
    TextComponent message = prefixedComponent(channel, content);
    for (Player player : Bukkit.getOnlinePlayers()) {
      player.spigot().sendMessage(message);
    }
  }

  public static void npcMessage(Player player, String name, String content) {
    Objects.requireNonNull(player, "player").sendMessage(
      "§d[NPC] " +
      Objects.requireNonNull(name, "name") +
      "§7:§r " +
      Objects.requireNonNull(content, "content")
    );
  }

  public static void attachCommand(
    TextComponent component,
    String command,
    String hoverText
  ) {
    Objects.requireNonNull(component, "component");
    String normalizedCommand = Objects.requireNonNull(
      command,
      "command"
    ).strip();
    if (normalizedCommand.startsWith("/")) {
      normalizedCommand = normalizedCommand.substring(1);
    }
    if (normalizedCommand.isEmpty()) {
      throw new IllegalArgumentException("command cannot be blank");
    }
    component.setClickEvent(
      new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + normalizedCommand)
    );
    attachHover(
      component,
      hoverText == null
        ? "§aClick to run:\n\n§b/" + normalizedCommand
        : hoverText
    );
  }

  public static void attachCommand(
    Player player,
    TextComponent component,
    String command
  ) {
    attachCommand(
      component,
      command,
      Locale.get(
        Objects.requireNonNull(player, "player"),
        "general.text.runcommand"
      ) +
      command
    );
  }

  public static void attachHover(TextComponent component, String hoverText) {
    Objects.requireNonNull(component, "component").setHoverEvent(
      new HoverEvent(
        HoverEvent.Action.SHOW_TEXT,
        new Text(
          new ComponentBuilder(
            hoverText == null ? "§eThere is nothing to see here!" : hoverText
          ).create()
        )
      )
    );
  }

  public static void attachOpenURL(TextComponent component, String url) {
    Objects.requireNonNull(component, "component");
    component.setText(component.getText() + " ↗§r");
    component.setClickEvent(
      new ClickEvent(
        ClickEvent.Action.OPEN_URL,
        Objects.requireNonNull(url, "url")
      )
    );
  }

  public static void attachCopy(TextComponent component, String value) {
    Objects.requireNonNull(component, "component");
    component.setText(component.getText() + " ⎘");
    component.setClickEvent(
      new ClickEvent(
        ClickEvent.Action.COPY_TO_CLIPBOARD,
        Objects.requireNonNull(value, "value")
      )
    );
  }

  public static String plural(long number) {
    return number == 1L ? "" : "s";
  }

  public static String intToTierCode(int number) {
    return "T" + number;
  }

  public static String intToRoman(int number) {
    if (number <= 0 || number > 3999) {
      throw new IllegalArgumentException("number must be between 1 and 3999");
    }
    int[] values = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    String[] numerals = {
      "M",
      "CM",
      "D",
      "CD",
      "C",
      "XC",
      "L",
      "XL",
      "X",
      "IX",
      "V",
      "IV",
      "I",
    };
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < values.length; index++) {
      while (number >= values[index]) {
        result.append(numerals[index]);
        number -= values[index];
      }
    }
    return result.toString();
  }

  public static List<String> fromArgs(String... values) {
    return List.of(values.clone());
  }

  public static List<String> reversedFromArgs(String... values) {
    List<String> result = new ArrayList<>(List.of(values.clone()));
    java.util.Collections.reverse(result);
    return List.copyOf(result);
  }

  public static List<String> fromObjects(Object... values) {
    List<String> result = new ArrayList<>(values.length);
    for (Object value : values) {
      result.add(String.valueOf(value).toLowerCase(java.util.Locale.ROOT));
    }
    return List.copyOf(result);
  }

  public static String wrapWithColor(String text, Locale.Language language) {
    Objects.requireNonNull(language, "language");
    int width = switch (language) {
      case ZH_CN, ZH_TW, JA_JP, KO_KR -> 15;
      default -> 30;
    };
    return wrapTextColor(text, width);
  }

  public static String wrapText(String text, int maxWidth) {
    if (text == null || maxWidth < 1) {
      return "";
    }
    StringBuilder result = new StringBuilder();
    for (String paragraph : text.split("\\R", -1)) {
      if (!result.isEmpty()) {
        result.append('\n');
      }
      wrapParagraph(result, paragraph, maxWidth);
    }
    return result.toString();
  }

  public static String wrapTextColor(String text, int maxWidth) {
    if (text == null || maxWidth < 1) {
      return "";
    }
    StringBuilder result = new StringBuilder();
    String activeFormatting = "";
    for (String paragraph : text.split("\\R", -1)) {
      if (!result.isEmpty()) {
        result.append('\n');
      }
      String wrapped = wrapText(paragraph, maxWidth);
      String[] lines = wrapped.split("\\n", -1);
      for (int index = 0; index < lines.length; index++) {
        if (index > 0) {
          result.append('\n');
          result.append(activeFormatting);
        }
        result.append(lines[index]);
        activeFormatting = ChatColor.getLastColors(
          activeFormatting + lines[index]
        );
      }
    }
    return result.toString();
  }

  public static List<String> wrap(String text, int maxWidth) {
    return List.of(wrapText(text, maxWidth).split("\\n", -1));
  }

  public static long[] convertMillisToTime(long milliseconds) {
    if (milliseconds < 0L) {
      throw new IllegalArgumentException("milliseconds cannot be negative");
    }
    long days = milliseconds / 86_400_000L;
    milliseconds %= 86_400_000L;
    long hours = milliseconds / 3_600_000L;
    milliseconds %= 3_600_000L;
    long minutes = milliseconds / 60_000L;
    milliseconds %= 60_000L;
    return new long[] { days, hours, minutes, milliseconds / 1_000L };
  }

  public static String randomString(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length cannot be negative");
    }
    StringBuilder result = new StringBuilder(length);
    for (int index = 0; index < length; index++) {
      result.append(
        RANDOM_CHARACTERS.charAt(
          ThreadLocalRandom.current().nextInt(RANDOM_CHARACTERS.length())
        )
      );
    }
    return result.toString();
  }

  public static String quantify(long amount, String content) {
    return "§7×" + amount + " " + Objects.requireNonNull(content, "content");
  }

  public static String commaNumber(long amount) {
    return String.format("%,d", amount);
  }

  private static TextComponent prefixedComponent(
    String channel,
    TextComponent content
  ) {
    TextComponent message = new TextComponent(
      TextComponent.fromLegacyText(channelPrefix(channel))
    );
    message.addExtra(Objects.requireNonNull(content, "content"));
    return message;
  }

  private static String channelPrefix(String channel) {
    String normalized = Objects.requireNonNull(channel, "channel").strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("channel cannot be blank");
    }
    return normalized.toUpperCase(java.util.Locale.ROOT) + CHANNEL_SUFFIX;
  }

  private static void wrapParagraph(
    StringBuilder output,
    String paragraph,
    int maxWidth
  ) {
    if (paragraph.isBlank()) {
      return;
    }
    StringBuilder line = new StringBuilder();
    for (String word : paragraph.strip().split("\\s+")) {
      int lineLength = visibleLength(line);
      int wordLength = visibleLength(word);
      if (lineLength > 0 && lineLength + 1 + wordLength > maxWidth) {
        output.append(line).append('\n');
        line.setLength(0);
      }
      if (!line.isEmpty()) {
        line.append(' ');
      }
      line.append(word);
    }
    output.append(line);
  }

  private static int visibleLength(CharSequence text) {
    Matcher matcher = LEGACY_COLOR.matcher(text);
    return matcher.replaceAll("").length();
  }
}
