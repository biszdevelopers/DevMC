package dev.bisz.enchants;

import dev.bisz.players.PlayerProfile;
import dev.bisz.players.Profile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Per-player preference controlling whether socket tooltips show descriptions. */
final class TooltipPreferences {
  private static final String KEY = "enchants:tooltips_collapsed";
  private static final Map<UUID, Boolean> CACHE = new HashMap<>();

  private TooltipPreferences() {}

  static boolean collapsed(Player viewer) {
    if (viewer == null) return false;
    return CACHE.computeIfAbsent(viewer.getUniqueId(), id -> read(viewer));
  }

  static boolean toggle(Player viewer) {
    boolean next = !collapsed(viewer);
    set(viewer, next);
    return next;
  }

  static void set(Player viewer, boolean collapsed) {
    CACHE.put(viewer.getUniqueId(), collapsed);
    Profile.setMetadata(viewer.getUniqueId(), KEY, collapsed);
  }

  static void forget(UUID id) { CACHE.remove(id); }

  private static boolean read(Player viewer) {
    try {
      PlayerProfile profile = Profile.findByOwner(viewer);
      Object value = profile == null ? null : profile.getMetadata(KEY);
      return Boolean.TRUE.equals(value);
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
