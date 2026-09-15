package dev.bisz.enchants.fishing;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.TextDisplay;

/** Floating countdown label above a fishing bobber. */
final class FishingIndicator {
  private final TextDisplay display;

  @SuppressWarnings("deprecation")
  private FishingIndicator(FishHook hook) {
    this.display = hook.getWorld().spawn(hook.getLocation().add(0, 1.2D, 0), TextDisplay.class, entity -> {
      entity.setBillboard(Display.Billboard.CENTER);
      entity.setSeeThrough(true);
      entity.setShadowed(false);
      entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
    });
  }

  static FishingIndicator spawn(FishHook hook) { return new FishingIndicator(hook); }

  void update(FishHook hook, int seconds, boolean afk) {
    if (!display.isValid()) return;
    display.teleport(hook.getLocation().add(0, 1.2D, 0));
    display.setText(afk ? "§a⏳ " + seconds + "s (AFK)" : "§e⏳ " + seconds + "s");
  }

  void remove() {
    if (display.isValid()) display.remove();
  }
}
