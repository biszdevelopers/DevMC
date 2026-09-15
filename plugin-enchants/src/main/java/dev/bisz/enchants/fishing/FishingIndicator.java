package dev.bisz.enchants.fishing;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.TextDisplay;

/** Floating countdown label that tracks a fishing bobber. */
final class FishingIndicator {
  private static final double OFFSET_Y = 1.2D;
  private static final double MIN_MOVE_SQUARED = 0.01D;

  private final TextDisplay display;
  private Location last;
  private int seconds = -1;
  private boolean afk;

  @SuppressWarnings("deprecation")
  private FishingIndicator(FishHook hook) {
    Location location = hook.getLocation().add(0, OFFSET_Y, 0);
    last = location.clone();
    this.display = hook.getWorld().spawn(location, TextDisplay.class, entity -> {
      entity.setBillboard(Display.Billboard.CENTER);
      entity.setSeeThrough(true);
      entity.setShadowed(false);
      entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
    });
  }

  static FishingIndicator spawn(FishHook hook) { return new FishingIndicator(hook); }

  /** Keeps the label above the bobber, skipping imperceptible movements. */
  void follow(FishHook hook) {
    if (!display.isValid()) return;
    Location target = hook.getLocation().add(0, OFFSET_Y, 0);
    if (last != null && last.getWorld() == target.getWorld()
      && last.distanceSquared(target) < MIN_MOVE_SQUARED) return;
    display.teleport(target);
    last = target;
  }

  void update(FishHook hook, int seconds, boolean afk) {
    follow(hook);
    if (this.seconds == seconds && this.afk == afk) return;
    this.seconds = seconds;
    this.afk = afk;
    if (!display.isValid()) return;
    display.setText(afk ? "§a⏳ " + seconds + "s (AFK)" : "§e⏳ " + seconds + "s");
  }

  void remove() {
    if (display.isValid()) display.remove();
  }
}
