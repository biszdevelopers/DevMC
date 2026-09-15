package dev.bisz.enchants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

/** Floating, per-entity deduplicated damage numbers. */
public final class DamageIndicator {
  private static boolean enabled = true;
  private static final Map<UUID, TextDisplay> active = new HashMap<>();

  private DamageIndicator() {}

  static void setEnabled(boolean value) { enabled = value; }
  static boolean isEnabled() { return enabled; }

  @SuppressWarnings("deprecation")
  public static void show(Plugin plugin, Entity target, double damage) {
    if (!enabled || target == null) return;
    // The indicator is an independent display, so a lethal hit must still show
    // it even though the target is already dying or removed.
    Location location;
    try {
      location = target.getLocation().add(0, target.getHeight() + 0.4, 0);
    } catch (RuntimeException exception) {
      return;
    }
    if (location.getWorld() == null) return;
    UUID targetId = target.getUniqueId();
    TextDisplay previous = active.remove(targetId);
    if (previous != null && previous.isValid()) previous.remove();

    TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
      entity.setText("§c" + format(damage));
      entity.setBillboard(Display.Billboard.CENTER);
      entity.setSeeThrough(true);
      entity.setShadowed(false);
      entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
    });
    active.put(targetId, display);

    int[] ticks = {0};
    plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
      ticks[0]++;
      if (!display.isValid()) {
        active.remove(targetId);
        task.cancel();
        return;
      }
      display.teleport(display.getLocation().add(0, 0.1, 0));
      if (ticks[0] >= 20) {
        active.remove(targetId);
        display.remove();
        task.cancel();
      }
    }, 0L, 1L);
  }

  static String format(double damage) {
    double value = damage <= 0D ? 0D : Math.max(.1D, Math.ceil(damage * 10D - 1e-9D) / 10D);
    return String.format(java.util.Locale.US, "%.1f", value);
  }
}
