package dev.bisz.players;

import java.util.Locale;
import org.bukkit.ChatColor;

/**
 * The server's persisted player ranks.
 */
public enum Rank {
  NONE(1L, 1L, "§7玩家", false),
  MOD(2L, 1L, "§a先知", false),
  ADMIN(3L, 300L, "§e管理员", true);

  private final long id;
  private final long level;
  private final String displayName;
  private final boolean grantsOperator;

  Rank(long id, long level, String displayName, boolean grantsOperator) {
    this.id = id;
    this.level = level;
    this.displayName = displayName;
    this.grantsOperator = grantsOperator;
  }

  public long id() {
    return id;
  }

  public long level() {
    return level;
  }

  public String displayName() {
    return displayName;
  }

  public boolean grantsOperator() {
    return grantsOperator;
  }

  public ChatColor color() {
    return ChatColor.getByChar(displayName.charAt(1));
  }

  public String colorPrefix() {
    return displayName.substring(0, 2);
  }

  public static Rank fromId(long id) {
    for (Rank rank : values()) {
      if (rank.id == id) {
        return rank;
      }
    }
    return NONE;
  }

  public static Rank fromName(String name) {
    if (name == null) {
      return NONE;
    }
    try {
      return valueOf(name.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return NONE;
    }
  }
}
