package dev.bisz.enchants;

import org.bukkit.entity.Player;

/** Player experience represented by a configurable constant points per level. */
public final class LinearExperience {
  /** trueMC's default linear cost, read from {@code xp.points-per-level}. */
  public static final int DEFAULT_POINTS_PER_LEVEL = 20;

  private static int pointsPerLevel = DEFAULT_POINTS_PER_LEVEL;

  private LinearExperience() {}

  /** The configured XP required for each level. */
  public static int pointsPerLevel() { return pointsPerLevel; }

  static void setPointsPerLevel(int value) { pointsPerLevel = Math.max(1, value); }

  public static int totalPoints(Player player) {
    return totalPoints(player.getLevel(), player.getExp());
  }

  static int totalPoints(int level, float progress) {
    long points = (long) Math.max(0, level) * pointsPerLevel
      + Math.round(Math.max(0F, Math.min(1F, progress)) * pointsPerLevel);
    return (int) Math.min(Integer.MAX_VALUE, points);
  }

  static int vanillaTotalPoints(int level, float progress) {
    int safeLevel = Math.max(0, level);
    long base = safeLevel <= 16
      ? (long) safeLevel * safeLevel + 6L * safeLevel
      : safeLevel <= 31
        ? Math.round(2.5D * safeLevel * safeLevel - 40.5D * safeLevel + 360D)
        : Math.round(4.5D * safeLevel * safeLevel - 162.5D * safeLevel + 2220D);
    int next = safeLevel <= 15
      ? 2 * safeLevel + 7
      : safeLevel <= 30 ? 5 * safeLevel - 38 : 9 * safeLevel - 158;
    return (int) Math.min(Integer.MAX_VALUE,
      base + Math.round(Math.max(0F, Math.min(1F, progress)) * next));
  }

  public static void addPoints(Player player, int points) {
    setTotalPoints(player, saturatedAdd(totalPoints(player), points));
  }

  public static void removeLevels(Player player, int levels) {
    if (levels <= 0) return;
    addPoints(player, saturatedMultiply(-levels, pointsPerLevel));
  }

  public static void setTotalPoints(Player player, int points) {
    int safePoints = Math.max(0, points);
    player.setLevel(safePoints / pointsPerLevel);
    player.setExp((safePoints % pointsPerLevel) / (float) pointsPerLevel);
    player.setTotalExperience(safePoints);
  }

  private static int saturatedAdd(int left, int right) {
    return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) left + right));
  }

  private static int saturatedMultiply(int left, int right) {
    long result = (long) left * right;
    return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result));
  }
}
