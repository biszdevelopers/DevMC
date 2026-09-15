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
final class DamageIndicator {
  private static boolean enabled = true;
  private static final Map<UUID, TextDisplay> active = new HashMap<>();

  private DamageIndicator() {}

  static void setEnabled(boolean value) { enabled = value; }
  static boolean isEnabled() { return enabled; }

  @SuppressWarnings("deprecation")
  static void show(Plugin plugin, Entity target, double damage) {
    if (!enabled || target == null || target.isDead()) return;
    TextDisplay previous = active.remove(target.getUniqueId());
    if (previous != null && previous.isValid()) previous.remove();

    Location location = target.getLocation().add(0, target.getHeight() + 0.4, 0);
    TextDisplay display = target.getWorld().spawn(location, TextDisplay.class, entity -> {
      entity.setText("§c" + format(damage));
      entity.setBillboard(Display.Billboard.CENTER);
      entity.setSeeThrough(true);
      entity.setShadowed(false);
      entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
    });
    active.put(target.getUniqueId(), display);

    int[] ticks = {0};
    plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
      ticks[0]++;
      if (!display.isValid()) {
        active.remove(target.getUniqueId());
        task.cancel();
        return;
      }
      display.teleport(display.getLocation().add(0, 0.1, 0));
      if (ticks[0] >= 20) {
        active.remove(target.getUniqueId());
        display.remove();
        task.cancel();
      }
    }, 0L, 1L);
  }

  private static String format(double damage) {
    double rounded = Math.round(damage * 10) / 10.0;
    return rounded == Math.floor(rounded) ? String.valueOf((int) rounded) : String.valueOf(rounded);
  }
}
