package dev.bisz.npc;

import org.bukkit.Location;

public interface NpcNavigator {
  void navigateTo(Location target);
  void cancel();
  boolean navigating();
}
